package org.evomaster.client.java.instrumentation.heuristic;

import org.evomaster.client.java.instrumentation.heuristic.validator.*;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.metadata.BeanDescriptor;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorHeuristicsTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    public void testNoConstraintsBean(){

        NoConstraintsBean bean = new NoConstraintsBean();

        assertThrows(IllegalArgumentException.class, () -> ValidatorHeuristics.computeTruthness(validator, bean));
    }

    @Test
    public void testBaseConstraintsBean(){

        BaseConstraintsBean bean = new BaseConstraintsBean();

        BeanDescriptor descriptor = validator.getConstraintsForClass(BaseConstraintsBean.class);
        assertTrue(descriptor.isBeanConstrained());
        assertEquals(4, descriptor.getConstrainedProperties().size());
        assertEquals(5, descriptor.getConstrainedProperties().stream()
                .flatMap(it -> it.getConstraintDescriptors().stream())
                .count()
        );

        Set<ConstraintViolation<BaseConstraintsBean>> result =  validator.validate(bean);
        //5 constraints, over 4 fields, 1 with none, 1 with 2 constraints.
        // 2 max constraints are satisfied (default int value is 0)
        assertEquals(3, result.size());
    }

    @Test
    public void testSingleConstraintBean(){

        SingleConstraintBean bean = new SingleConstraintBean();

        BeanDescriptor descriptor = validator.getConstraintsForClass(SingleConstraintBean.class);
        assertTrue(descriptor.isBeanConstrained());
        assertEquals(1, descriptor.getConstrainedProperties().size());
        assertEquals(1, descriptor.getConstrainedProperties().stream()
                .flatMap(it -> it.getConstraintDescriptors().stream())
                .count()
        );

        bean.x = 42;

        Set<ConstraintViolation<SingleConstraintBean>> result =  validator.validate(bean);
        assertEquals(0, result.size());

        bean.x = -5;

        result =  validator.validate(bean);
        assertEquals(1, result.size());

        ConstraintViolation<SingleConstraintBean> failure = result.stream().findFirst().get();
        assertEquals(-5, failure.getInvalidValue());
        assertEquals(Min.class, failure.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals(1L, failure.getConstraintDescriptor().getAttributes().get("value"));
    }


    @Test
    public void testHeuristicForSingleConstraintBean(){

        SingleConstraintBean bean = new SingleConstraintBean(); //Min 1
        bean.x = 42;

        Truthness t = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t.isTrue());
        assertFalse(t.isFalse());

        bean.x = -100;
        Truthness tm100 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertFalse(tm100.isTrue());
        assertTrue(tm100.isFalse());

        bean.x = -5;
        Truthness tm5 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertFalse(tm5.isTrue());
        assertTrue(tm5.isFalse());

        assertTrue(tm5.getOfTrue() > tm100.getOfTrue());


        bean.x = 1;
        Truthness t1 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t1.isTrue());
        assertFalse(t1.isFalse());
    }


    @Test
    public void testHeuristicForIntBean(){

        IntBean bean = new IntBean();

        Truthness t0 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertFalse(t0.isTrue());

        bean.a = 50; //min 42
        Truthness t1 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertFalse(t1.isTrue());
        assertTrue(t1.getOfTrue() > t0.getOfTrue());

        bean.b = 1000; // making worse, as max 666
        Truthness t2 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertFalse(t2.isTrue());
        assertTrue(t1.getOfTrue() > t2.getOfTrue());


        bean.b = 33;
        bean.c = -3; // between -5 and -2
        Truthness t3 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertFalse(t3.isTrue());
        assertTrue(t3.getOfTrue() > t1.getOfTrue());

        bean.d = 1; // 0 is not Positive
        Truthness t4 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertFalse(t4.isTrue());
        assertTrue(t4.getOfTrue() > t3.getOfTrue());

        bean.f = -1;
        Truthness t5 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t5.isTrue());
        assertFalse(t5.isFalse());
    }

    @Test
    public void testHeuristicForString(){

        StringBean bean = new StringBean();

        Truthness t0 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertFalse(t0.isTrue());

        bean.a = "foo"; //@NotNull
        Truthness t1 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t1.getOfTrue() > t0.getOfTrue());

        bean.b = "foo"; //@Null
        Truthness t2 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t1.getOfTrue() > t2.getOfTrue()); // worse


        bean.b = null; //@Null
        bean.c = "    ";
        Truthness t3 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t3.getOfTrue() > t1.getOfTrue());

        bean.d = "hello"; //@NotBlank
        Truthness t4 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t4.getOfTrue() > t3.getOfTrue());

        bean.d = "   ";
        Truthness t5 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t4.getOfTrue() > t5.getOfTrue()); //worse

        bean.d = "hello";
        bean.e = "eeeee"; //@Pattern
        Truthness t6 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertEquals(t4.getOfTrue(), t6.getOfTrue(), 0.00001); // null pattern is valid

        bean.e = "eeeeehhhh"; //@Pattern
        Truthness t7 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t6.getOfTrue() > t7.getOfTrue()); //worse


        bean.e = "ee"; //@Pattern
        bean.f = "1"; //@Size 2-5
        Truthness t8 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t6.getOfTrue() > t8.getOfTrue()); //worse, as null was valid

        bean.f = "123456789"; //@Size 2-5
        Truthness t9 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t8.getOfTrue() > t9.getOfTrue()); //worse, as more away from range

        bean.f = "1234"; //@Size 2-5
        Truthness t10 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertEquals(t10.getOfTrue(), t6.getOfTrue(), 0.00001); // null and valid size are the same

        bean.g = Arrays.asList("a");
        Truthness t11 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t10.getOfTrue() > t11.getOfTrue()); //worse, as null was valid

        bean.g = Arrays.asList("a","b");
        Truthness t12 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertEquals(t10.getOfTrue(), t12.getOfTrue(), 0.00001); // null and valid size are the same

        bean.h = Arrays.asList();
        Truthness t13 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t13.getOfTrue() > t12.getOfTrue()); // better than null

        bean.h = Arrays.asList("a");
        Truthness t14 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t14.getOfTrue() > t13.getOfTrue()); // better than empty

        bean.i = new String[]{"foo"};
        Truthness t15 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t15.getOfTrue() > t14.getOfTrue());

        bean.l = new HashMap<>();
        Truthness t16 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t15.getOfTrue() > t16.getOfTrue()); //worse, as null was valid

        bean.l.put("A","A");
        Truthness t17 = ValidatorHeuristics.computeTruthness(validator, bean);
        assertTrue(t17.getOfTrue() > t16.getOfTrue());

        assertTrue(t17.isTrue());
    }
}