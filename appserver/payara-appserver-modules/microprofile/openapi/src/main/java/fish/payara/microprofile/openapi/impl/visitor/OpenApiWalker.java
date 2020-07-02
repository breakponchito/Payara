/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2018-2020] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.microprofile.openapi.impl.visitor;

import fish.payara.microprofile.openapi.api.visitor.ApiContext;
import fish.payara.microprofile.openapi.api.visitor.ApiVisitor;
import fish.payara.microprofile.openapi.api.visitor.ApiVisitor.VisitorFunction;
import fish.payara.microprofile.openapi.api.visitor.ApiWalker;
import fish.payara.microprofile.openapi.impl.model.util.AnnotationInfo;
import static fish.payara.microprofile.openapi.impl.model.util.ModelUtils.getOperation;
import static fish.payara.microprofile.openapi.impl.model.util.ModelUtils.getResourcePath;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toSet;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.ExternalDocumentation;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.callbacks.Callback;
import org.eclipse.microprofile.openapi.annotations.callbacks.Callbacks;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.extensions.Extensions;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirements;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.eclipse.microprofile.openapi.annotations.servers.Servers;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;
import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.glassfish.hk2.classmodel.reflect.AnnotatedElement;
import org.glassfish.hk2.classmodel.reflect.AnnotationModel;
import org.glassfish.hk2.classmodel.reflect.ClassModel;
import org.glassfish.hk2.classmodel.reflect.FieldModel;
import org.glassfish.hk2.classmodel.reflect.MethodModel;
import org.glassfish.hk2.classmodel.reflect.Type;

/**
 * A walker that visits each annotation and passes it to the visitor.
 */
public class OpenApiWalker implements ApiWalker {

    private static final Logger LOGGER = Logger.getLogger(OpenApiWalker.class.getName());

    private final OpenAPI api;
    private final Set<Type> types;
    private final Map<String, Set<String>> resourceMapping;
    private final ClassLoader appClassLoader;

    public OpenApiWalker(OpenAPI api, Set<Type> types, ClassLoader appClassLoader) {
        this.api = api;
        this.types = new TreeSet<>(Comparator.comparing(Type::getName, String::compareTo));
        this.types.addAll(types);
        this.appClassLoader = appClassLoader;
        this.resourceMapping = generateResourceMapping(types);
    }

    @Override
    public void accept(ApiVisitor visitor) {
        // OpenAPI necessary annotations
        processAnnotations(OpenAPIDefinition.class, visitor::visitOpenAPI);

        // JAX-RS methods
        processAnnotations(GET.class, visitor::visitGET);
        processAnnotations(POST.class, visitor::visitPOST);
        processAnnotations(PUT.class, visitor::visitPUT);
        processAnnotations(DELETE.class, visitor::visitDELETE);
        processAnnotations(HEAD.class, visitor::visitHEAD);
        processAnnotations(OPTIONS.class, visitor::visitOPTIONS);
        processAnnotations(PATCH.class, visitor::visitPATCH);

        // JAX-RS parameters
        processAnnotations(QueryParam.class, visitor::visitQueryParam);
        processAnnotations(PathParam.class, visitor::visitPathParam);
        processAnnotations(HeaderParam.class, visitor::visitHeaderParam);
        processAnnotations(CookieParam.class, visitor::visitCookieParam);
        processAnnotations(FormParam.class, visitor::visitFormParam);

        // All other OpenAPI annotations
        processAnnotations(Schema.class, visitor::visitSchema);
        processAnnotations(Server.class, visitor::visitServer, Servers.class);
        processAnnotations(Servers.class, visitor::visitServers, Server.class);
        processAnnotations(Extensions.class, visitor::visitExtensions, Extension.class);
        processAnnotations(Extension.class, visitor::visitExtension, Extensions.class);
        processAnnotations(Operation.class, visitor::visitOperation);
        processAnnotations(Callback.class, visitor::visitCallback, Callbacks.class);
        processAnnotations(Callbacks.class, visitor::visitCallbacks, Callback.class);
        processAnnotations(APIResponse.class, visitor::visitAPIResponse, APIResponses.class);
        processAnnotations(APIResponses.class, visitor::visitAPIResponses, APIResponse.class);
        processAnnotations(Parameters.class, visitor::visitParameters, Parameter.class);
        processAnnotations(Parameter.class, visitor::visitParameter, Parameters.class);
        processAnnotations(ExternalDocumentation.class, visitor::visitExternalDocumentation);
        processAnnotations(Tag.class, visitor::visitTag, Tags.class);
        processAnnotations(Tags.class, visitor::visitTags, Tag.class);
        processAnnotations(SecurityScheme.class, visitor::visitSecurityScheme, SecuritySchemes.class);
        processAnnotations(SecuritySchemes.class, visitor::visitSecuritySchemes, SecurityScheme.class);
        processAnnotations(SecurityRequirement.class, visitor::visitSecurityRequirement, SecurityRequirements.class);
        processAnnotations(SecurityRequirements.class, visitor::visitSecurityRequirements, SecurityRequirement.class);

        // JAX-RS response types
        processAnnotations(Produces.class, visitor::visitProduces);
        processAnnotations(Consumes.class, visitor::visitConsumes);

        // OpenAPI response types
        processAnnotations(RequestBody.class, visitor::visitRequestBody);
        //redo schema, now all others have been to ensure sub-schemas work
        processAnnotations(Schema.class, visitor::visitSchema);
    }

    @SafeVarargs
    private final <A extends Annotation, E extends AnnotatedElement> void processAnnotations(
            Class<A> annotationClass, VisitorFunction<AnnotationModel, E> annotationFunction, 
            Class<? extends Annotation>... alternatives) {

        for (Type type : types) {
            if(type instanceof ClassModel) {
                processAnnotation((ClassModel)type, annotationClass, annotationFunction, alternatives);
            }
        }

    }

    @SafeVarargs
    private final <A extends Annotation, E extends AnnotatedElement> void processAnnotation(
            ClassModel annotatedClass, Class<A> annotationClass, VisitorFunction<AnnotationModel, E> annotationFunction,
            Class<? extends Annotation>... alternatives) {
        AnnotationInfo annotations = AnnotationInfo.valueOf(annotatedClass);
        processAnnotation(annotatedClass, annotationClass, annotationFunction, annotations,
                new OpenApiContext(types, appClassLoader, api, getResourcePath(annotatedClass, resourceMapping)), alternatives);

        for (final FieldModel field : annotatedClass.getFields()) {
            if (annotations.isAnnotationPresent(annotationClass, field)) {
                if (annotationClass == HeaderParam.class
                        || annotationClass == CookieParam.class
                        || annotationClass == PathParam.class
                        || annotationClass == QueryParam.class) {
                    // NB. if fields are annotated as Param all methods have it
                    for (MethodModel method : annotatedClass.getMethods()) {
                        OpenApiContext context = new OpenApiContext(types, appClassLoader, api,
                                getResourcePath(method, resourceMapping),
                                getOperation(method, api, resourceMapping));
                        if (context.getWorkingOperation() != null) {
                            processAnnotation(field, annotationClass, annotationFunction, annotations, context,
                                    alternatives);
                        }
                    }
                } else {
                    processAnnotation(field, annotationClass, annotationFunction, annotations,
                            new OpenApiContext(types, appClassLoader, api, null), alternatives);
                }
            }
        }

        for (final MethodModel method : annotatedClass.getMethods()) {
            OpenApiContext context = new OpenApiContext(types, appClassLoader, api,
                    getResourcePath(method, resourceMapping),
                    getOperation(method, api, resourceMapping));
            processAnnotation(method, annotationClass, annotationFunction, annotations, context, alternatives);

            for (org.glassfish.hk2.classmodel.reflect.Parameter parameter : method.getParameters()) {
                processAnnotation(parameter, annotationClass, annotationFunction, annotations, context, alternatives);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @SafeVarargs
    private static <A extends Annotation, E extends AnnotatedElement> void processAnnotation(
            AnnotatedElement element,
            Class<A> annotationClass,
            VisitorFunction<AnnotationModel, E> annotationFunction,
            AnnotationInfo annotations,
            ApiContext context,
            Class<? extends Annotation>... alternatives
    ) {
        // If it's just the one annotation class
        // Check the element
        if (annotations.isAnnotationPresent(annotationClass, element)) {
            annotationFunction.apply(annotations.getAnnotation(annotationClass, element), (E) element, context);
        } else if (element instanceof MethodModel && annotations.isAnnotationPresent(annotationClass)
                && !annotations.isAnyAnnotationPresent(element, alternatives)) {
            // If the method isn't annotated, inherit the class annotation
            if (context.getPath() != null) {
                annotationFunction.apply(annotations.getAnnotation(annotationClass), (E) element, context);
            }
        }
    }

    /**
     * Generates a map listing the location each resource class is mapped to.
     */
    private Map<String, Set<String>> generateResourceMapping(Set<Type> types) {
        Set<String> classList = new HashSet<>();
        Map<String, Set<String>> mapping = new HashMap<>();
        for (Type type : types) {
            if(type instanceof ClassModel) {
                ClassModel classModel = (ClassModel) type;
                if(classModel.getAnnotation(ApplicationPath.class.getName()) != null) {
                    // Produce the mapping
                    AnnotationModel annotation = classModel.getAnnotation(ApplicationPath.class.getName());
                    String key = annotation.getValue("value", String.class);
                    Set<String> resourceClasses = new HashSet<>();
                    mapping.put(key, resourceClasses);
                    try {
                        Class<?> clazz = appClassLoader.loadClass(classModel.getName());
                        Application app = (Application) clazz.newInstance();
                        // Add all classes contained in the application
                        resourceClasses.addAll(app.getClasses()
                                .stream()
                                .map(Class::getName).collect(toSet()));
                        // Remove all Jersey providers
                        resourceClasses.removeIf(resource -> resource.contains("org.glassfish.jersey"));
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                        LOGGER.log(WARNING, "Unable to initialise application class.", ex);
                    }
                } else {
                    classList.add(classModel.getName());
                }
            }
        }

        // If there is one application and it's empty, add all classes
        if (mapping.keySet().size() == 1) {
            Set<String> classes = mapping.values().iterator().next();
            if (classes.isEmpty()) {
                classes.addAll(classList);
            }
        }

        // If there is no application, add all classes to the context root.
        if (mapping.isEmpty()) {
            mapping.put("/", classList);
        }

        return mapping;
    }
}
