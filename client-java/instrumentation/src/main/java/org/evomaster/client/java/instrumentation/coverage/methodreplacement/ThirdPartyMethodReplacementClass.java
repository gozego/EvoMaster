package org.evomaster.client.java.instrumentation.coverage.methodreplacement;

import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.instrumentation.staticstate.UnitsInfoRecorder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Third-party libraries might or might not be on the classpath.
 * Furthermore, they MUST NOT be part of EvoMaster.
 * So we have to use reflection to access them at runtime.
 *
 * There is a problem though :(
 * The replaced methods might have inputs or outputs from the third-party library.
 * To write replacements, we need to create the right method signatures, and those must
 * be available at compilation time.
 * A previous attempt to solve this issue was to include those dependencies, but with "provided" scope (so
 * they will not be included in the uber jar).
 * Unfortunately, this does NOT work, as the classloader that loads the instrumentation might be different from
 * the one used for the SUT. This is the case for example for Spring applications when using External Driver.
 *
 * The current solution is to use reflection, and have such 3rd-party library NOT on the classpath.
 * They can be in "test" scope when running tests (eg to check validity of string constants), though.
 *
 * Still, this leaves issue with method signatures.
 * For return types using 3rd-party objects, must put Object as return type, with actual type specified
 * in "castTo". A forced casting is automatically then done at instrumentation time.
 * For input parameters, will need to use the ThirdPartyCast annotation.
 *
 * There is still the issue of which classloader to use for reflection.
 * For MR of non-static methods, can use classloader of the original caller.
 * For the other cases (eg, static methods and constructors), need to retrieve appropriate classloader from
 * UnitInfoRecorder.
 *
 */
public abstract class ThirdPartyMethodReplacementClass implements MethodReplacementClass{

    private Class<?> targetClass;

    private boolean triedToLoad = false;

    /**
     * Key -> id defined in @Replacement
     * Value -> original target method that was replaced
     */
    private final Map<String, Method> methods = new HashMap<>();

    /**
     * Key -> id defined in @Replacement
     * Value -> original target method that was replaced
     */
    private final Map<String, Constructor> constructors = new HashMap<>();

    protected ThirdPartyMethodReplacementClass(){

//        if(! isAvailable()){
//            //nothing to initialize
//            return;
//        }
        //initMethods();
    }

    private  void initMethods() {
    /*
        Use reflection to load all methods that were replaced.
        This is essential to simplify the writing of the replacement, as those
        must still call the original, but only via reflection (as original third-party
        library must not included in EvoMaster)
     */
        Class<? extends ThirdPartyMethodReplacementClass> subclass = this.getClass();

        for (Method m : subclass.getDeclaredMethods()) {

            Replacement r = m.getAnnotation(Replacement.class);
            if (r == null || r.id().isEmpty()) {
                continue;
            }
            if (r.replacingConstructor())
                continue;

            Class[] inputs = m.getParameterTypes();
            Annotation[][] annotations = m.getParameterAnnotations();

            int start = 0;
            if(!r.replacingStatic()){
                start = 1;
            }

            int end = inputs.length-1;
            if(r.type() == ReplacementType.TRACKER){
                //no idTemplate at the end
                end = inputs.length;
            }

            Class[] reducedInputs = Arrays.copyOfRange(inputs, start, end);

            for (int i = start; i < end; i++){
                if (annotations[i].length > 0) {
                    Class<?> klazz = ReplacementUtils.getCastedToThirdParty(annotations[i]);
                    if (klazz != null)
                        reducedInputs[i-start] = klazz;
                }
            }

            Method targetMethod;
            try {
                //this will not return private methods
                targetMethod = getTargetClass().getMethod(m.getName(), reducedInputs);
            } catch (NoSuchMethodException e) {
                try {
                    //this would return private methods, but not public in superclasses
                    targetMethod = targetClass.getDeclaredMethod(m.getName(), reducedInputs);
                } catch (NoSuchMethodException noSuchMethodException) {
                    throw new RuntimeException("BUG in EvoMaster: " + e);
                }
            }

            String id = r.id();

            if(methods.containsKey(id)){
                throw new IllegalStateException("Non-unique id: " + id);
            }

            methods.put(id, targetMethod);

        }
    }

    private  void initConstructors() {

        Class<? extends ThirdPartyMethodReplacementClass> subclass = this.getClass();

        for (Method m : subclass.getDeclaredMethods()) {

            Replacement r = m.getAnnotation(Replacement.class);

            if (r == null || r.id().isEmpty()) {
                continue;
            }

            if (!r.replacingConstructor())
                continue;

            Class[] inputs = m.getParameterTypes();

            int start = 0;

            int end = inputs.length-1;
            if(r.type() == ReplacementType.TRACKER){
                //no idTemplate at the end
                end = inputs.length;
            }

            Class[] reducedInputs = Arrays.copyOfRange(inputs, start, end);

            Constructor targetConstructor = null;
            try {
                targetConstructor = targetClass.getConstructor(reducedInputs);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("BUG in EvoMaster: " + e);
            }

            String id = r.id();

            if(constructors.containsKey(id)){
                throw new IllegalStateException("Non-unique id: " + id);
            }

            constructors.put(id, targetConstructor);

        }
    }

    protected abstract String getNameOfThirdPartyTargetClass();

    /**
     *
     * @param singleton a reference to an instance of the subclass. As reflection is expensive,
     *                  we suggest to create it only once, and save it in final static field
     * @param id    of a replacement method
     * @return  original method that was replaced
     */
    public static Method getOriginal(ThirdPartyMethodReplacementClass singleton, String id, Object obj){
        if(id == null || id.isEmpty()){
            throw new IllegalArgumentException("Invalid empty id");
        }

        Objects.requireNonNull(obj);

        if(singleton.getTargetClass()==null){
            /*
                    This is tricky. We did a method replacement, but the class is not accessible at runtime
                    from the class loader of the instrumentation... so we try it from the caller
             */
            singleton.retryLoadingClass(obj.getClass().getClassLoader());
        }

        if(singleton.methods.isEmpty()){
            singleton.initMethods();
        }
        Method original = singleton.methods.get(id);
        if(original == null){
            throw new IllegalArgumentException("No method exists with id: " + id);
        }
        return original;
    }


    /**
     *
     * @param singleton a reference to an instance of the subclass. As reflection is expensive,
     *                  we suggest to create it only once, and save it in final static field
     * @param id    of a replacement method
     * @return  original constructor that was replaced
     */
    public static Constructor getOriginalConstructor(ThirdPartyMethodReplacementClass singleton, String id){
        if(id == null || id.isEmpty()){
            throw new IllegalArgumentException("Invalid empty id");
        }

        if(singleton.getTargetClass()==null){

            /*
                we do not have access to the caller directly here, so we need to use what registered
                in ExecutionTracer
             */

            String callerName = ExecutionTracer.getLastCallerClass();
            if(callerName == null){
                //this would be clearly a bug...
                throw new IllegalStateException("No access to last caller class");
            }
            //TODO what if more than 1 available ???
            ClassLoader loader = UnitsInfoRecorder.getInstance().getClassLoaders(callerName).get(0);

            singleton.retryLoadingClass(loader);
        }

        if(singleton.constructors.isEmpty()){
            singleton.initConstructors();
        }
        Constructor original = singleton.constructors.get(id);
        if(original == null){
            throw new IllegalArgumentException("No constructor exists with id: " + id);
        }
        return original;
    }

    private void retryLoadingClass(ClassLoader classLoader) {
        try {
            targetClass = classLoader.loadClass(getTargetClassName());
            triedToLoad = true;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("ISSUE IN EVOMASTER: classloader problems when dealing with: " + getTargetClassName());
        }
    }

    @Override
    public Class<?> getTargetClass() {

        if(targetClass != null){
            return targetClass;
        }

        /*
            If not present, try to load it via reflection based on the class name.
            But try only once
         */
        if(!triedToLoad){
            triedToLoad = true;

            try{
                targetClass = Class.forName(getTargetClassName());
            }catch (Exception e){
                //this can happen if the third-party library is missing.
                //it is not a bug/error
            }
        }

        return targetClass;
    }

    @Override
    public final String getTargetClassName() {
        return getNameOfThirdPartyTargetClass();
    }
}
