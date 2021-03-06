/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.jpsg.collect;

import net.openhft.jpsg.*;
import net.openhft.jpsg.collect.algo.hash.*;
import net.openhft.jpsg.collect.bulk.*;
import net.openhft.jpsg.collect.iter.IterMethod;
import net.openhft.jpsg.collect.mapqu.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.openhft.jpsg.GeneratorTask.DIMENSIONS;
import static java.lang.String.format;


public class MethodGeneratingProcessor extends TemplateProcessor {
    private static final int PRIORITY = DEFAULT_PRIORITY - 10;

    private static final Pattern METHOD_BODY_P = RegexpUtils.compile(
            "(?<indentSpaces>[^\\S\\n]*+)/[\\*/]\\s*template\\s+(?<methodName>\\S+)" +
                    format("\\s*(with%s)?", DIMENSIONS) +
                    "\\s*[\\*/]/" +
                    "([^\\S\\n]*$|[^/]*?/[\\*/]\\s*endtemplate\\s*[\\*/]/)");

    private static final Map<String, Class<? extends Method>> METHODS =
            new HashMap<String, Class<? extends Method>>() {
                {
                    // bulk
                    put(ForEachWhile.class);
                    put(ReversePutAllTo.class);
                    put(ReverseAddAllTo.class);
                    put(ReverseRemoveAllFrom.class);
                    put(AllContainingIn.class);
                    put(RemoveIf.class);
                    put(ReplaceAll.class);
                    put(ForEach.class);
                    put(ToArray.class);
                    put(ToTypedArray.class);
                    put(ToPrimitiveArray.class);
                    put(RemoveAll.class);
                    put(RetainAll.class);
                    put(SetHashCode.class);
                    put(ListHashCode.class);
                    put(ToString.class);

                    // hash-only
                    put(Rehash.class);
                    put(Index.class);
                    put(Insert.class);
                    put(ValueIndex.class);

                    // map query/update
                    put(ContainsEntry.class);
                    put(ContainsKey.class);
                    put(Get.class);
                    put(GetOrDefault.class);
                    put(Remove.class);
                    put(JustRemove.class);
                    put(RemoveEntry.class);
                    put(Put.class);
                    put(PutIfAbsent.class);
                    put(JustPut.class);
                    put(IncrementValue.class);
                    put(IncrementValueWithDefault.class);
                    put(Compute.class);
                    put(ComputeIfAbsent.class);
                    put(ComputeIfPresent.class);
                    put(Merge.class);
                    put(Replace.class);
                    put(ReplaceEntry.class);
                    put(Add.class);
                }

                private void put(Class<? extends Method> c) {
                    put(c.getSimpleName().toLowerCase(), c);
                }
            };

    private static String generate(String methodName, Context cxt, String indent) {
        Method method;
        MethodGenerator generator;
        if (methodName.toLowerCase().startsWith("iterator.")) {
            method = IterMethod.forName(methodName.split("\\.")[1]);
            generator = new HashIteratorMethodGenerator();
        } else if (methodName.toLowerCase().startsWith("cursor.")) {
            method = IterMethod.forName(methodName.split("\\.")[1]);
            generator = new HashCursorMethodGenerator();
        } else {
            Class<? extends Method> methodClass = METHODS.get(methodName.toLowerCase());
            if (methodClass == null) {
                throw new RuntimeException("Unknown method: " + methodName);
            }
            try {
                method = methodClass.newInstance();
                if (method instanceof BulkMethod) {
                    generator = new HashBulkMethodGenerator();
                } else if (method instanceof MapQueryUpdateMethod) {
                    generator = new HashMapQueryUpdateMethodGenerator();
                } else {
                    throw new RuntimeException();
                }
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return generator.generate(new MethodContext(cxt), indent, method);
    }

    @Override
    protected int priority() {
        return PRIORITY;
    }

    @Override
    protected void process(Context source, Context target, String template) {
        Matcher matcher = METHOD_BODY_P.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String methodName = matcher.group("methodName");
            String indentSpaces = matcher.group("indentSpaces");
            String additionalContextDims = matcher.group("dimensions");
            if (additionalContextDims != null) {
                List<Context> addContexts = getDimensionsParser().parse(additionalContextDims)
                        .generateContexts();
                if (addContexts.size() != 1) {
                    throw new IllegalStateException();
                }
                target = target.join(addContexts.get(0));
            }
            try {
                String generated = generate(methodName, target, indentSpaces);
                matcher.appendReplacement(sb,
                        Matcher.quoteReplacement(generated.replaceFirst("\\s+$", "")));
            } catch (RuntimeException e) {
                throw new RuntimeException(
                        "Runtime exception while generating " + methodName + " template", e);
            }

        }
        matcher.appendTail(sb);
        postProcess(source, target, sb.toString());
    }
}
