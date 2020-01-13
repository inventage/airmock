package com.inventage.airmock.waf.ui;

import com.inventage.airmock.kernel.RouteProvider;
import com.inventage.airmock.kernel.route.RoutingContextUtils;
import com.inventage.airmock.kernel.util.AirmockConfigRetriever;
import com.inventage.airmock.waf.AirmockHandler;
import com.inventage.airmock.waf.cookiebag.CookieBag;
import com.inventage.airmock.waf.cookiebag.Cookiemanager;
import com.inventage.airmock.waf.headerbag.Headermanager;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static com.inventage.airmock.waf.headerbag.HeaderBag.NAME_FIELD;
import static com.inventage.airmock.waf.headerbag.HeaderBag.VALUE_FIELD;
import static com.inventage.airmock.kernel.route.RoutingContextUtils.session;
import static java.util.Objects.nonNull;

/**
 * Verticle that offers a very small UI for the AirmockWaf.
 */
public class WafUiVerticle extends AbstractVerticle implements RouteProvider {

    public static final String PATH_PREFIX = "path-prefix";
    public static final Logger LOGGER = LoggerFactory.getLogger(WafUiVerticle.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private JsonObject config;

    /**
     * Get the fully qualified property for a given key.
     *
     * @param key the configuration key within this class
     * @return the fully qualified property (as used for env variables)
     */
    public static String property(String key) {
        return WafUiVerticle.class.getName() + "." + key;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        // Get the config
        final ConfigRetriever configRetriever = AirmockConfigRetriever.create(vertx);
        configRetriever.getConfig(result -> {
            if (result.succeeded()) {
                config = result.result();
                startFuture.succeeded();
            }
            else {
                startFuture.fail(result.cause());
            }
        });
    }

    @Override
    public Router createRoutes() {
        final Router router = Router.router(vertx);

        router.get("/session/terminate").handler(this::terminateSession);
        router.get("/roles/:role/remove").handler(this::removeRoleFromSession);
        router.get("/cookiebags/:mapping/delete").handler(this::deleteCookiesOfMapping);
        router.get("/*").handler(this::ui);

        return router;
    }

    private void removeRoleFromSession(RoutingContext routingContext) {
        final String role = routingContext.pathParam("role");
        LOGGER.info("removeRoleFromSession: role '{}'", role, routingContext);
        AirmockHandler.removeRoleFromSession(role, routingContext);
        routingContext.response().setStatusCode(HttpResponseStatus.FOUND.code()).putHeader(HttpHeaders.LOCATION.toString(), "/waf").end();
    }

    private void terminateSession(RoutingContext routingContext) {
        final Session session = RoutingContextUtils.session(routingContext);
        LOGGER.info("terminateSession: session with id '{}'", session.id(), routingContext);
        session.destroy();
        routingContext.response().setStatusCode(HttpResponseStatus.FOUND.code()).putHeader(HttpHeaders.LOCATION.toString(), "/waf").end();
    }

    private void deleteCookiesOfMapping(RoutingContext routingContext) {
        final String mapping = routingContext.pathParam("mapping");
        LOGGER.debug("deleteCookiesOfMapping: mapping '{}'", mapping, routingContext);
        routingContext.response().setStatusCode(HttpResponseStatus.FOUND.code()).putHeader(HttpHeaders.LOCATION.toString(), "/waf").end();
    }

    private void ui(RoutingContext rc) {
        rc.response().putHeader(HttpHeaders.CONTENT_TYPE.toString(), "text/html;charset=utf-8").end(mePage(rc));
    }

    private String mePage(RoutingContext rc) {
        final StringBuilder page = new StringBuilder();

        page
                .append("<html>")
                .append("<link rel=\"icon\" href=\"data:,\">")
                .append("<h1>Airmock</h1>")
                .append(session(rc))
                .append(mappingSection(rc))
                .append(roleSection(rc))
                .append(cookieBagSection(rc))
                .append(headerBagSection(rc))
                .append("</html>");

        return page.toString();
    }

    private String cookieBagSection(RoutingContext rc) {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("<h2>Cookie Bag</h2>");
        final JsonObject cookieBag = (JsonObject) RoutingContextUtils.session(rc).get(Cookiemanager.COOKIE_BAG);
        if (cookieBag != null) {
            buffer.append("<ul>");
            cookieBag.stream()
                    .map(entry -> (Map.Entry<String, Object>) entry)
                    .forEach(pathCookies -> {
                        final String path = pathCookies.getKey();
                        buffer.append("<li>");
                        buffer.append(path);
                        final Object value = ((Map.Entry) pathCookies).getValue();
                        if (value instanceof JsonArray) {
                            buffer.append("<ul>");
                            final JsonArray cookies = (JsonArray) value;
                            cookies.stream().map(entry -> (JsonObject) entry).forEach(cookie -> {
                                buffer.append("<li>");
                                buffer.append(pre(cookie.getString("name") + "=" + cookie.getString("value")));
                                if (showJWTDecodingLink(cookie)) {
                                    buffer.append(" [");
                                    buffer.append("<a target=_blank href=\"https://jwt.io?token=")
                                            .append(cookie.getString(CookieBag.VALUE_FIELD))
                                            .append("\">show</a>");
                                    buffer.append("]");
                                }
                                buffer.append("</li>");
                            });
                            buffer.append("</ul>");
                        }
                        buffer.append("</li>");
                    });
            buffer.append("</ul>");
        }
        return buffer.toString();
    }

    private boolean showJWTDecodingLink(JsonObject cookie) {
        final String cookieName = cookie.getString("name");
        return cookieName.endsWith("_token") ||
                cookieName.endsWith("KEYCLOAK_IDENTITY") ||
                "jwt".equals(cookieName);
    }

    private String headerBagSection(RoutingContext rc) {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("<h2>Header Bag</h2>");
        final JsonArray headerBag = RoutingContextUtils.session(rc).get(Headermanager.HEADER_BAG);
        if (headerBag != null) {
            buffer.append("<ul>");
            headerBag.stream()
                    .map(e -> (JsonObject) e)
                    .forEach(header -> {
                        buffer.append("<li>");
                        if (header instanceof JsonObject) {
                            buffer.append(pre(header.getString(NAME_FIELD) + "=" + header.getString(VALUE_FIELD)));
                        }
                        buffer.append("</li>");
                    });
            buffer.append("</ul>");
        }
        return buffer.toString();
    }

    private String mappingSection(RoutingContext rc) {
        final Session session = RoutingContextUtils.session(rc);
        final StringBuffer buffer = new StringBuffer();
        buffer.append("<h2>Mapping URLs</h2>");
        final JsonArray mappings = getMappings();
        buffer.append("<ul>");
        if (nonNull(mappings)) {
            mappings.stream()
                .map(mapping -> (JsonObject) mapping)
                .forEach(mapping -> {
                    buffer.append("<li>");
                    buffer.append("<a target=\"_blank\" href=\"")
                            .append(mapping.getString("contextRoot"))
                            .append("\"> [")
                            .append(mapping.getString("contextRoot"))
                            .append("]</a> ");
                    buffer.append(session.get(mapping.getString("contextRoot")) != null ? " @ "
                        + DATE_TIME_FORMATTER.format(session.get(mapping.getString("contextRoot"))) : "");
                    buffer.append("</li>");
                });
        }
        buffer.append("</ul>");

        return buffer.toString();
    }

    private String roleSection(RoutingContext rc) {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("<h2>Roles</h2>");

        final JsonArray wafRoles = RoutingContextUtils.session(rc).get("WAF_ROLES");
        if (wafRoles != null) {
            buffer.append("<ul>");
            wafRoles.stream().map(role -> (JsonObject) role).forEach(role -> {
                buffer
                        .append("<li>")
                        .append(code(role.toString())).append(" [<a href=\"/waf/roles/").append(role.getString("name")).append("/remove\">remove</a>]")
                        .append("</li>");

            });
            buffer.append("</ul>");
        }

        return buffer.toString();
    }

    private JsonArray getMappings() {
        final String pathToConfig = config.getString(AirmockHandler.PREFIX + AirmockHandler.PATH_TO_CONFIG);
        JsonArray mappingsArray = new JsonArray();
        try {
            final String config = readFile(pathToConfig, StandardCharsets.UTF_8);
            final JsonObject entries = new JsonObject(config);
            mappingsArray = entries.getJsonArray("mappings");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return mappingsArray;
    }

    private String session(RoutingContext rc) {
        final Session session = RoutingContextUtils.session(rc);
        final StringBuilder html = new StringBuilder();
        html
                .append(p(
                        "WAF Session Cookie: "
                                + code("airmock=" + session.id())
                                + " [<a href=\"/waf/session/terminate\">terminate</a>]"
                ))
                .append(p(
                        "WAF Audit Token: "
                                + code(session.get(Cookiemanager.AUDIT_TOKEN))
                ));

        return html.toString();
    }

    private String p(String string) {
        return "<p>" + string + "</p>";
    }

    private String code(String string) {
        return "<code>" + string + "</code>";
    }

    private String readFile(String path, Charset encoding) throws IOException {
        final byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    private String pre(String string) {
        return "<pre style='white-space: pre-wrap; word-break: break-all;'>" + string + "</pre>";
    }
}
