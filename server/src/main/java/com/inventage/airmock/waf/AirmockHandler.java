package com.inventage.airmock.waf;

import com.inventage.airmock.kernel.util.ConfigUtils;
import com.inventage.airmock.waf.cookiebag.Cookiemanager;
import com.inventage.airmock.waf.headerbag.Headermanager;
import com.inventage.airmock.waf.mapping.*;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.inventage.airmock.kernel.Constants.JWT_COOKE_NAME;
import static com.inventage.airmock.kernel.Constants.STORED_JWT_KEY;
import static com.inventage.airmock.kernel.route.RoutingContextUtils.session;
import static com.inventage.airmock.waf.cookiebag.Cookiemanager.AUDIT_TOKEN;

/**
 * Handler class for the AirmockWaf.
 */
public class AirmockHandler implements Handler<RoutingContext> {
    public static final String PATH_TO_CONFIG = "config-path";
    public static final String PREFIX = AirmockHandler.class.getName() + ".";

    private static final String WAF_ROLES = "WAF_ROLES";
    private static final Logger LOGGER = LoggerFactory.getLogger(AirmockHandler.class);

    private static final String API_COOKIE = "AL_CONTROL";

    private static final String SET_COOKIE = HttpHeaders.SET_COOKIE.toString();

    private final List<Mapping> mappings = new ArrayList<>();

    public AirmockHandler(JsonObject config, Router router) {
        LOGGER.info("--------------------------------------------------------------------");
        LOGGER.info("  __   __  ____  _  _   __    ___  __ _    _  _   __   ____");
        LOGGER.info(" / _\\ (  )(  _ \\( \\/ ) /  \\  / __)(  / )  / )( \\ / _\\ (  __)");
        LOGGER.info("/    \\ )(  )   // \\/ \\(  O )( (__  )  (   \\ /\\ //    \\ ) _)");
        LOGGER.info("\\_/\\_/(__)(__\\_)\\_)(_/ \\__/  \\___)(__\\_)  (_/\\_)\\_/\\_/(__)");
        LOGGER.info("--envs:-------------------------------------------------------------");
        String pathToMappingsConfig = "PROPERTY NOT FOUND";
        try {
            logConfig(config);
            pathToMappingsConfig = config.getString(PREFIX + PATH_TO_CONFIG);
            final String wafConfig = readFile(pathToMappingsConfig, Charset.forName("UTF-8"));
            LOGGER.info("--config:-----------------------------------------------------------");
            LOGGER.info("using mappings from '{}': \n{}", pathToMappingsConfig, wafConfig);
            LOGGER.info("--------------------------------------------------------------------");
            applyConfig(wafConfig, config, router);
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Could not read mappings: " + pathToMappingsConfig);
        }
    }

    /**
     * Remove selected role from session.
     *
     * @param roleToBeRemoved name of the role
     * @param routingContext  rc
     */
    public static void removeRoleFromSession(String roleToBeRemoved, RoutingContext routingContext) {
        LOGGER.info("removeRoleFromSession: role '{}'", roleToBeRemoved, routingContext);
        final Session session = session(routingContext);
        final JsonArray roles = session.get(WAF_ROLES);
        if (roles != null && roles.size() > 0) {
            final JsonArray newRoles = new JsonArray();
            roles.stream()
                .map(item -> (JsonObject) item)
                .filter(role -> !(role.getString("name").equals(roleToBeRemoved)))
                .forEach(newRoles::add);
            session.remove(WAF_ROLES);
            session.put(WAF_ROLES, newRoles);
        }
    }

    private void logConfig(JsonObject config) {
        config.stream().filter(entry -> ofInterest(entry)).sorted().forEach(property -> LOGGER.info("{}", property));
    }

    private String readFile(String path, Charset encoding) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Property 'com.inventage.airmock.waf.AirmockHandler.config-path' not defined.");
        }
        else {
            final byte[] encoded = Files.readAllBytes(Paths.get(path));
            return new String(encoded, encoding);
        }
    }

    private void applyConfig(String wafConfig, JsonObject config, Router router) {
        final JsonObject entries = new JsonObject(wafConfig);
        final JsonArray mappingsArray = entries.getJsonArray("mappings");
        if (mappingsArray != null) {
            mappingsArray.stream().forEach(entry -> {
                final JsonObject mapping = (JsonObject) entry;
                final Mapping newMapping = createMapping(config, mapping, router);
                mappings.add(newMapping);
                LOGGER.info("mapping added '{}'", newMapping);
            });
        }
    }

    private Mapping createMapping(JsonObject config, JsonObject mapping, Router router) {
        final ConfigUtils configUtils = new ConfigUtils(config);
        final String name = mapping.getString("name");
        final String contextRoot = mapping.getString("contextRoot");
        final String deniedAccessUrl = configUtils.replaceEnvVariables(mapping.getString("deniedAccessUrl"));
        final MappingFlowType authenticationFlow = authenticationFlow(mapping,"authenticationFlow");
        final JsonArray restrictedToRoles = jsonArray(mapping,"restrictedToRoles");
        final String[] objects = restrictedToRoles.stream().map(Object::toString).collect(Collectors.toList()).toArray(new String[0]);
        final JsonArray headersArray = jsonArray(mapping,"headers");
        final List<String> headers = headersArray.stream().map(Object::toString).collect(Collectors.toList());
        final JsonObject backend = mapping.getJsonObject("backend");
        final Map<String, String> mappingConfig = getMappingConfig(mapping);

        final DefaultMapping newMapping = createConcreteMapping(authenticationFlow);

        newMapping.init(configUtils, name, contextRoot, objects, deniedAccessUrl,
            headers, backendHost(backend, config), backendPort(backend, config), mappingConfig);

        return newMapping;
    }

    private JsonArray jsonArray(JsonObject mapping, String property) {
        final JsonArray jsonArray = mapping.getJsonArray(property);
        return jsonArray == null ? new JsonArray() : jsonArray;
    }

    private MappingFlowType authenticationFlow(JsonObject mapping, String property) {
        final String mappingString = mapping.getString("authenticationFlow");
        if (mappingString == null) {
            return MappingFlowType.CODE_401;
        }
        else {
            return MappingFlowType.valueOf(mappingString);
        }
    }

    DefaultMapping createConcreteMapping(MappingFlowType mappingFlowType) {
        switch (mappingFlowType) {
            case ONESHOT:
                return new OneShotMapping();
            case TOKENEXCHANGE:
                return new TokenExchangeMapping();
            case CODE_401:
                return new Unauthorized401Mapping();
            case CODE_403:
                return new Forbidden403Mapping();
            case REDIRECT:
                return new RedirectMapping();
            default:
                throw new IllegalArgumentException("Unknown MappingFlowType " + mappingFlowType.name());
        }

    }

    private Map<String, String> getMappingConfig(JsonObject mapping) {
        final Map<String, String> result = new HashMap<>();

        final JsonObject mappingConfigJson = mapping.getJsonObject("config");
        if (mappingConfigJson != null) {
            mappingConfigJson
                    .getMap()
                    .forEach((key, value) -> result.put(key, value.toString()));
        }
        return result;
    }

    private boolean ofInterest(Map.Entry<String, Object> entry) {
        return entry.getKey().startsWith("com.inventage.airmock.Applications") ||
            entry.getKey().startsWith("com.inventage.airmock.waf.AirlockHandler");
    }

    private Mapping getMapping(String path) {
        return mappings.stream().filter(mapping -> mapping.isMatching(path)).findFirst().orElse(new DenyAllMapping());
    }

    private String backendHost(JsonObject backend, JsonObject config) {
        return backend == null ? "" : substituteStringVariable(backend.getString("host"), config);
    }
    private int backendPort(JsonObject backend, JsonObject config) {
        return backend == null ? 0 : substituteIntegerVariable(backend.getValue("port"), config);
    }
    private String substituteStringVariable(String value, JsonObject config) {
        return (String) substituteVariable(value, config);
    }
    private Integer substituteIntegerVariable(Object value, JsonObject config) {
        return new Double(substituteVariable(value, config)).intValue();
    }
    private String substituteVariable(Object value, JsonObject config) {
        if (value instanceof String) {
            final String variable = (String) value;
            if (variable.startsWith("${") && variable.endsWith("}")) {
                return String.valueOf(config.getValue(variable.substring(2, variable.length() - 1)));
            }
        }
        return String.valueOf(value);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        LOGGER.debug("handle: intercepting '{}'", routingContext.request().path(), routingContext);
        final Mapping mapping = getMapping(routingContext.request().path());

        mapping.canProceed(routingContext, this).subscribe(canProceed -> {
            if (canProceed) {
                Cookiemanager.prepareCookieHeaderForRequest(routingContext);
                Headermanager.prepareHeaderForRequest(routingContext);
                routingContext.addHeadersEndHandler(v -> addSetCookieForJWT(routingContext, mapping));
                routingContext.addHeadersEndHandler(v -> intercepted(routingContext, mapping));
                routingContext.data().put(Mapping.class.getName(), mapping);
                routingContext.next(); // send request to backend
            }
            else {
                mapping.endRequest(routingContext);
            }
        }, error -> LOGGER.error("could not handle the request, error: '{}'", error, routingContext));
    }

    private void intercepted(RoutingContext routingContext, Mapping mapping) {
        LOGGER.info("intercepted: executing '{}'", routingContext.request().path(), routingContext);
        handleHeaders(routingContext, mapping);
    }

    private void handleHeaders(RoutingContext routingContext, Mapping mapping) {
        final MultiMap headers = routingContext.response().headers();
        final Set<String> names = headers.names();
        names.forEach(name -> {
            LOGGER.debug("handleHeaders: response with header '{}' and value '{}'", name, headers.getAll(name), routingContext);
        });
        if (headers.contains(SET_COOKIE)) {
            final List<String> setCookiesValue = headers.getAll(SET_COOKIE);
            handleCookies(routingContext, setCookiesValue, mapping);
            Cookiemanager.storeCookies(routingContext, setCookiesValue, mapping);
            LOGGER.info("handleHeaders: removing header 'Set-Cookie' and value '{}'", setCookiesValue, routingContext);
            headers.remove(SET_COOKIE);
        }
    }

    private void addSetCookieForJWT(RoutingContext routingContext, Mapping mapping) {
        final Object jwt = routingContext.data().get(STORED_JWT_KEY);
        if (jwt != null && jwt instanceof String) {
            final MultiMap headers = routingContext.response().headers();
            headers.add(SET_COOKIE, getValueForSetCookie((String) jwt));
        }
    }

    private String getValueForSetCookie(String jwt) {
        return JWT_COOKE_NAME + "=" + jwt;
    }

    /**
     * Add cookies to rc.
     *
     * @param routingContext  rc
     * @param setCookiesValue cookies
     * @param currentMapping  current mapping
     */
    public void handleCookies(RoutingContext routingContext, List<String> setCookiesValue, Mapping currentMapping) {
        setCookiesValue.stream()
            .map(HttpCookie::parse)
            .flatMap(cookies -> cookies.stream())
            .forEach(cookie -> processControlApi(cookie, routingContext));
    }

    private boolean processControlApi(HttpCookie cookie, RoutingContext routingContext) {
        if (API_COOKIE.equals(cookie.getName())) {
            try {
                final String cookieValue = URLDecoder.decode(cookie.getValue(), "UTF8");
                final String[] controlCommand = cookieValue.split("&");
                Arrays.stream(controlCommand).forEach(command -> {
                    try {
                        final String[] controlCookie = command.split("=");
                        if (controlCookie.length > 0) {
                            final String cookieName = controlCookie[0];
                            if ("ADD_CREDENTIALS".equalsIgnoreCase(cookieName) && controlCookie.length == 2) {
                                final String apiValue = URLDecoder.decode(controlCookie[1], "UTF8");
                                final String[] roles = apiValue.split(",");
                                Arrays.stream(roles).forEach(role -> addRoleToSession(role, routingContext));
                            }
                            if ("SET_CREDENTIALS".equalsIgnoreCase(cookieName) && controlCookie.length == 2) {
                                removeAllRolesFromSession(routingContext);
                                final String apiValue = URLDecoder.decode(controlCookie[1], "UTF8");
                                final String[] roles = apiValue.split(",");
                                Arrays.stream(roles).forEach(role -> addRoleToSession(role, routingContext));
                            }
                            if ("AUDIT_TOKEN".equalsIgnoreCase(cookieName) && controlCookie.length == 2) {
                                final String apiValue = URLDecoder.decode(controlCookie[1], "UTF8");
                                addAuditTokenToSession(apiValue, routingContext);
                            }
                            if ("SESSION".equalsIgnoreCase(cookieName) && controlCookie.length == 2) {
                                final String apiValue = URLDecoder.decode(controlCookie[1], "UTF8");
                                LOGGER.info("processControlApi: SESSION '{}'", apiValue, routingContext);
                                // send propagate logout to all mappings
                                propagateLogout(routingContext);
                                session(routingContext).destroy();
                            }
                        }
                    }
                    catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                });
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return false;
        }
        else {
            return true;
        }
    }

    private void addAuditTokenToSession(String auditToken, RoutingContext routingContext) {
        session(routingContext).put(AUDIT_TOKEN, auditToken);
    }

    private void propagateLogout(RoutingContext routingContext) {
        mappings.stream().forEach(mapping -> mapping.logout(routingContext));
    }

    private void removeAllRolesFromSession(RoutingContext routingContext) {
        session(routingContext).put(WAF_ROLES, new JsonArray());
    }

    private void addRoleToSession(String roleDefinition, RoutingContext routingContext) {
        final JsonObject role = parseRoleDefinition(roleDefinition);
        if (role != null) {
            JsonArray roles = session(routingContext).get(WAF_ROLES);
            if (roles == null) {
                roles = new JsonArray();
            }
            roles.add(role);
            LOGGER.info("addRoleToSession: role '{}'", role, routingContext);
            session(routingContext).put(WAF_ROLES, roles);
        }
    }

    // roleDefinition := credential-name [ ":" credential-timeout [ ":" credential-lifetime ]
    private JsonObject parseRoleDefinition(String roleDefinition) {
        JsonObject result = null;
        if (roleDefinition != null && !roleDefinition.isEmpty()) {
            final String[] roleElements = roleDefinition.split(":");
            if (roleElements.length > 0) {
                result = new JsonObject();
                result.put("name", roleElements[0]);
            }
            if (roleElements.length > 1) {
                result.put("idleTimeout", roleElements[1]);
            }
            if (roleElements.length > 2) {
                result.put("roleLifetime", roleElements[2]);
            }
        }
        return result;
    }
}
