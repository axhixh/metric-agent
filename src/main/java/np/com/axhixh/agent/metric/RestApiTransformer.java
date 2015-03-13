package np.com.axhixh.agent.metric;

import javassist.*;

import javax.ws.rs.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Optional;

/**
 * Created by ashish on 11/03/15.
 */
public class RestApiTransformer implements ClassFileTransformer {

    private ClassPool classPool;

    public RestApiTransformer() {
        classPool = new ClassPool();
        classPool.appendSystemPath();
        try {
            classPool.appendPathList(System.getProperty("java.class.path"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

        if (isBoringName(className)) {
            return null;
        }

        CtClass cc = null;
        try {
            String fqName = className.replace('/', '.');
            classPool.appendClassPath(new ByteArrayClassPath(fqName, classfileBuffer));
            cc = classPool.get(fqName);
            Optional<String> baseUrlOption = getRestUrl(cc);
            if (baseUrlOption.isPresent()) {
                String basePath = baseUrlOption.get();
                for (CtMethod m : cc.getDeclaredMethods()) {
                    Optional<CtMethod> annotatedMethod = getAnnotatedMethod(m);
                    if (annotatedMethod.isPresent()) {
                        String httpMethod = getHttpMethod(annotatedMethod.get());
                        String url = basePath;
                        if (annotatedMethod.get().hasAnnotation(Path.class)) {
                            url += ((Path) annotatedMethod.get().getAnnotation(Path.class)).value();
                        }

                        String metric = httpMethod + '.' + url;
                        m.addLocalVariable("__elapsedTime", CtClass.longType);
                        m.insertBefore("__elapsedTime = System.currentTimeMillis();");
                        m.insertAfter("{__elapsedTime = System.currentTimeMillis() - __elapsedTime;"
                                + " np.com.axhixh.agent.metric.Agent.report(\"" + metric + "\",  __elapsedTime);}");
                    }
                }

                byte[] byteCode = cc.toBytecode();
                return byteCode;
            }
        } catch (Exception ignoring) {
            ignoring.printStackTrace(System.err);
        } finally {
            if (cc != null) {
                cc.detach();
            }
        }
        return null;
    }

    private Optional<CtMethod> getAnnotatedMethod(CtMethod method) {
        if (method.hasAnnotation(GET.class) || method.hasAnnotation(POST.class) ||
                method.hasAnnotation(PUT.class) || method.hasAnnotation(DELETE.class) ||
                method.hasAnnotation(HttpMethod.class)) {
            return Optional.of(method);
        }

        // annotation on interface?
        final CtClass declaringClass = method.getDeclaringClass();
        try {
            for (CtClass classInterface : declaringClass.getInterfaces()) {
               if (classInterface.hasAnnotation(Path.class)) {
                    return Optional.of(classInterface.getDeclaredMethod(method.getName(), method.getParameterTypes()));
               }
            }
        } catch (NotFoundException e) {

        }

        return Optional.empty();
    }
    private String getHttpMethod(CtMethod method) {
        if (method.hasAnnotation(GET.class)) {
            return "GET";
        }

        if (method.hasAnnotation(POST.class)) {
            return "POST";
        }

        if (method.hasAnnotation(PUT.class)) {
            return "PUT";
        }

        if (method.hasAnnotation(DELETE.class)) {
            return "DELETE";
        }

        try {
            if (method.hasAnnotation(HttpMethod.class)) {
                return ((HttpMethod) method.getAnnotation(HttpMethod.class)).value();
            }
        } catch (ClassNotFoundException err) {
        }
        return "NONE";
    }

    private Optional<String> getRestUrl(CtClass classToCheck) throws ClassNotFoundException {
        if (classToCheck == null) {
            return Optional.empty();
        }
        if (classToCheck.isFrozen() || classToCheck.isInterface() || classToCheck.isAnnotation() ||
                classToCheck.isEnum() || classToCheck.isArray()) {
            return Optional.empty();
        }

        for (Object annotation: classToCheck.getAvailableAnnotations()) {
            if (annotation instanceof Path) {
                final String path = ((Path) annotation).value();
                return Optional.of(path);
            }
        }

        try {
            // interface has the annotations?
            for (CtClass ctInterface : classToCheck.getInterfaces()) {
                for (Object annotation : ctInterface.getAvailableAnnotations()) {
                    if (annotation instanceof Path) {
                        final String path = ((Path) annotation).value();
                        return Optional.of(path);
                    }
                }
            }
        } catch (NotFoundException ignored) {

        }

        return Optional.empty();
    }

    private boolean isBoringName(String name) {
        return name == null || // framework/jvm issue?
            name.startsWith("java") ||
                name.startsWith("sun/");

    }
}
