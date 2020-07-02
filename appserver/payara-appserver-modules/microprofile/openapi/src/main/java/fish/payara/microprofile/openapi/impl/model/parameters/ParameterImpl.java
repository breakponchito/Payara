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
package fish.payara.microprofile.openapi.impl.model.parameters;

import fish.payara.microprofile.openapi.api.visitor.ApiContext;
import fish.payara.microprofile.openapi.impl.model.ExtensibleImpl;
import fish.payara.microprofile.openapi.impl.model.examples.ExampleImpl;
import fish.payara.microprofile.openapi.impl.model.headers.HeaderImpl;
import fish.payara.microprofile.openapi.impl.model.media.ContentImpl;
import fish.payara.microprofile.openapi.impl.model.media.SchemaImpl;
import static fish.payara.microprofile.openapi.impl.model.util.ModelUtils.applyReference;
import static fish.payara.microprofile.openapi.impl.model.util.ModelUtils.mergeProperty;
import static fish.payara.microprofile.openapi.impl.processor.ApplicationProcessor.getValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.models.examples.Example;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.glassfish.hk2.classmodel.reflect.AnnotationModel;
import org.glassfish.hk2.classmodel.reflect.EnumModel;

public class ParameterImpl extends ExtensibleImpl<Parameter> implements Parameter {

    private String name;
    private In in;
    private String description;
    private Boolean required;
    private Boolean deprecated;
    private Boolean allowEmptyValue;
    private String ref;

    private Style style;
    private Boolean explode;
    private Boolean allowReserved;
    private Schema schema;
    private Map<String, Example> examples = new HashMap<>();
    private Object example;
    private Content content;
    private List<Content> contents = new ArrayList<>();

    public static Parameter createInstance(AnnotationModel annotation, ApiContext context) {
        ParameterImpl from = new ParameterImpl();
        from.setName(getValue("name", String.class, annotation));
        EnumModel inEnum = getValue("in", EnumModel.class, annotation);
        if (inEnum != null) {
            from.setIn(In.valueOf(inEnum.getValue()));
        }
        from.setDescription(getValue("description", String.class, annotation));
        from.setRequired(getValue("required", Boolean.class, annotation));
        from.setDeprecated(getValue("deprecated", Boolean.class, annotation));
        from.setAllowEmptyValue(getValue("allowEmptyValue", Boolean.class, annotation));
        String ref = getValue("ref", String.class, annotation);
        if (ref != null && !ref.isEmpty()) {
            from.setRef(ref);
        }
        EnumModel styleEnum = getValue("style", EnumModel.class, annotation);
        if (styleEnum != null) {
            from.setStyle(Style.valueOf(styleEnum.getValue()));
        }
        from.setExplode(getValue("explode", Boolean.class, annotation));
        from.setAllowReserved(getValue("allowReserved", Boolean.class, annotation));
        AnnotationModel schemaAnnotation = getValue("schema", AnnotationModel.class, annotation);
        if (schemaAnnotation != null) {
            from.setSchema(SchemaImpl.createInstance(schemaAnnotation, context));
        }
        List<AnnotationModel> examples = getValue("examples", List.class, annotation);
        if (examples != null) {
            for (AnnotationModel example : examples) {
                from.getExamples().put(
                        example.getValue("name", String.class),
                        ExampleImpl.createInstance(example)
                );
            }
        }
        from.setExample(getValue("example", Object.class, annotation));
        List<AnnotationModel> contentAnnotations = getValue("content", List.class, annotation);
        if (contentAnnotations != null && !contentAnnotations.isEmpty()) {
            from.setContent(new ContentImpl());
            for (AnnotationModel contentAnnotation : contentAnnotations) {
                from.getContents().add(
                        ContentImpl.createInstance(contentAnnotation, context)
                );
            }
        }
        return from;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public In getIn() {
        return in;
    }

    @Override
    public void setIn(In in) {
        this.in = in;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Boolean getRequired() {
        return required;
    }

    @Override
    public void setRequired(Boolean required) {
        this.required = required;
    }

    @Override
    public Boolean getDeprecated() {
        return deprecated;
    }

    @Override
    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

    @Override
    public Boolean getAllowEmptyValue() {
        return allowEmptyValue;
    }

    @Override
    public void setAllowEmptyValue(Boolean allowEmptyValue) {
        this.allowEmptyValue = allowEmptyValue;
    }

    @Override
    public Style getStyle() {
        return style;
    }

    @Override
    public void setStyle(Style style) {
        this.style = style;
    }

    @Override
    public Boolean getExplode() {
        return explode;
    }

    @Override
    public void setExplode(Boolean explode) {
        this.explode = explode;
    }

    @Override
    public Boolean getAllowReserved() {
        return allowReserved;
    }

    @Override
    public void setAllowReserved(Boolean allowReserved) {
        this.allowReserved = allowReserved;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    @Override
    public Map<String, Example> getExamples() {
        return examples;
    }

    @Override
    public void setExamples(Map<String, Example> examples) {
        this.examples = examples;
    }

    @Override
    public Parameter addExample(String key, Example example) {
        if (example != null) {
            this.examples.put(key, example);
        }
        return this;
    }

    @Override
    public void removeExample(String key) {
        this.examples.remove(key);
    }

    @Override
    public Object getExample() {
        return example;
    }

    @Override
    public void setExample(Object example) {
        this.example = example;
    }

    @Override
    public Content getContent() {
        return content;
    }

    @Override
    public void setContent(Content content) {
        this.content = content;
    }

    public List<Content> getContents() {
        return contents;
    }

    public void setContents(List<Content> contents) {
        this.contents = contents;
    }

    @Override
    public String getRef() {
        return ref;
    }

    @Override
    public void setRef(String ref) {
        if (ref != null && !ref.contains(".") && !ref.contains("/")) {
            ref = "#/components/parameters/" + ref;
        }
        this.ref = ref;
    }

    public static void merge(Parameter from, Parameter to,
            boolean override, ApiContext context) {
        if (from == null) {
            return;
        }
        if (from.getRef() != null && !from.getRef().isEmpty()) {
            applyReference(to, from.getRef());
            return;
        }
        to.setName(mergeProperty(to.getName(), from.getName(), override));
        to.setDescription(mergeProperty(to.getDescription(), from.getDescription(), override));
        if (from.getIn()!= null) {
            to.setIn(mergeProperty(to.getIn(),from.getIn(), override));
        }
        to.setRequired(mergeProperty(to.getRequired(), from.getRequired(), override));
        to.setDeprecated(mergeProperty(to.getDeprecated(), from.getDeprecated(), override));
        to.setAllowEmptyValue(mergeProperty(to.getAllowEmptyValue(), from.getAllowEmptyValue(), override));
        if (from.getStyle() != null){
            to.setStyle(mergeProperty(to.getStyle(), from.getStyle(), override));
        }
        if (from.getExplode() != null) {
            to.setExplode(mergeProperty(to.getExplode(), false, override));
        }
        to.setAllowReserved(mergeProperty(to.getAllowReserved(), from.getAllowReserved(), override));
        if (from.getSchema() != null) {
            if (to.getSchema() == null) {
                to.setSchema(new SchemaImpl());
            }
            SchemaImpl.merge(from.getSchema(), to.getSchema(), override, context);
        }
        to.setExample(mergeProperty(to.getExample(), from.getExample(), override));
        if (from.getExamples() != null) {
            for (String exampleName : from.getExamples().keySet()) {
                if (exampleName != null) {
                    Example example = new ExampleImpl();
                    ExampleImpl.merge(from.getExamples().get(exampleName), example, override);
                    to.addExample(exampleName, example);
                }
            }
        }
        if (from instanceof ParameterImpl) {
            ParameterImpl fromImpl = (ParameterImpl)from;
            if (fromImpl.getContents() != null) {
                if (to.getContent() == null) {
                    to.setContent(new ContentImpl());
                }
                for (Content content : fromImpl.getContents()) {
                    ContentImpl.merge(content, to.getContent(), override, context);
                }
            }

        }
        if (from.getContent() != null) {
            if (to.getContent() == null) {
                to.setContent(new ContentImpl());
            }
            ContentImpl.merge(from.getContent(), to.getContent(), override, context);
        }
    }

}
