package com.inventage.airmock.waf.mapping;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.jupiter.api.Test;

public class DenyAllMappingTest {


    @Test
    void canProceedCompletesTest() {
        //given
        DenyAllMapping denyAllMapping = new DenyAllMapping();
        TestObserver<Boolean> testObserver = new TestObserver<>();

        //when
        Single<Boolean> canProceed = denyAllMapping.canProceed(null, null);

        //then
        canProceed.subscribe(testObserver);

        testObserver.assertComplete();
    }

    @Test
    void canProceedNoErrorsTest() {
        //given
        DenyAllMapping denyAllMapping = new DenyAllMapping();
        TestObserver<Boolean> testObserver = new TestObserver<>();

        //when
        Single<Boolean> canProceed = denyAllMapping.canProceed(null, null);

        //then
        canProceed.subscribe(testObserver);

        testObserver.assertNoErrors();
    }

    @Test
    void canProceedIsFalseTest() {
        //given
        DenyAllMapping denyAllMapping = new DenyAllMapping();
        TestObserver<Boolean> testObserver = new TestObserver<>();

        //when
        Single<Boolean> canProceed = denyAllMapping.canProceed(null, null);

        //then
        canProceed.subscribe(testObserver);

        testObserver.assertValue(Boolean.FALSE);
    }
}
