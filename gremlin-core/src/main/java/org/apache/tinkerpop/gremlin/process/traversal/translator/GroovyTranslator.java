/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.tinkerpop.gremlin.process.traversal.translator;

import org.apache.commons.configuration2.ConfigurationConverter;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.tinkerpop.gremlin.process.traversal.Bytecode;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.SackFunctions;
import org.apache.tinkerpop.gremlin.process.traversal.Script;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.process.traversal.Translator;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalOptionParent;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.TraversalStrategyProxy;
import org.apache.tinkerpop.gremlin.process.traversal.util.ConnectiveP;
import org.apache.tinkerpop.gremlin.process.traversal.util.OrP;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.util.function.Lambda;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Converts bytecode to a Groovy string of Gremlin.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Stephen Mallette (http://stephen.genoprime.com)
 * @author Stark Arya (sandszhou.zj@alibaba-inc.com)
 */
public final class GroovyTranslator implements Translator.ScriptTranslator {

    private final String traversalSource;
    private final TypeTranslator typeTranslator;

    private GroovyTranslator(final String traversalSource, final TypeTranslator typeTranslator) {
        this.traversalSource = traversalSource;
        this.typeTranslator = typeTranslator;
    }

    /**
     * Creates the translator with a {@code false} argument to {@code withParameters} using
     * {@link #of(String, boolean)}.
     */
    public static GroovyTranslator of(final String traversalSource) {
        return of(traversalSource, false);
    }

    /**
     * Creates the translator with the {@link DefaultTypeTranslator} passing the {@code withParameters} option to it
     * which will handle type translation in a fashion that should typically increase cache hits and reduce
     * compilation times if enabled at the sacrifice to rewriting of the script that could reduce readability.
     */
    public static GroovyTranslator of(final String traversalSource, final boolean withParameters) {
        return of(traversalSource, new DefaultTypeTranslator(withParameters));
    }

    /**
     * Creates the translator with a custom {@link TypeTranslator} instance.
     */
    public static GroovyTranslator of(final String traversalSource, final TypeTranslator typeTranslator) {
        return new GroovyTranslator(traversalSource, typeTranslator);
    }

    @Override
    public Script translate(final Bytecode bytecode) {
        return typeTranslator.apply(traversalSource, bytecode);
    }

    @Override
    public String getTargetLanguage() {
        return "gremlin-groovy";
    }

    @Override
    public String toString() {
        return StringFactory.translatorString(this);
    }

    @Override
    public String getTraversalSource() {
        return this.traversalSource;
    }

    /**
     * Performs standard type translation for the TinkerPop types to Groovy.
     */
    public static class DefaultTypeTranslator extends AbstractTypeTranslator {

        public DefaultTypeTranslator(final boolean withParameters) {
            super(withParameters);
        }

        @Override
        protected String getNullSyntax() {
            return "null";
        }

        @Override
        protected String getSyntax(final String o) {
            return (o.contains("\"") ? "\"\"\"" + StringEscapeUtils.escapeJava(o) + "\"\"\"" : "\"" + StringEscapeUtils.escapeJava(o) + "\"")
                    .replace("$", "\\$");
        }

        @Override
        protected String getSyntax(final Boolean o) {
            return o.toString();
        }

        @Override
        protected String getSyntax(final Date o) {
            return "new java.util.Date(" + o.getTime() + ")";
        }

        @Override
        protected String getSyntax(final Timestamp o) {
            return "new java.sql.Timestamp(" + o.getTime() + ")";
        }

        @Override
        protected String getSyntax(final UUID o) {
            return "java.util.UUID.fromString('" + o.toString() + "')";
        }

        @Override
        protected String getSyntax(final Lambda o) {
            final String lambdaString = o.getLambdaScript().trim();
            return lambdaString.startsWith("{") ? lambdaString : "{" + lambdaString + "}";
        }

        @Override
        protected String getSyntax(final SackFunctions.Barrier o) {
            return "SackFunctions.Barrier." + o.toString();
        }

        @Override
        protected String getSyntax(final VertexProperty.Cardinality o) {
            return "VertexProperty.Cardinality." + o.toString();
        }

        @Override
        protected String getSyntax(final TraversalOptionParent.Pick o) {
            return "TraversalOptionParent.Pick." + o.toString();
        }

        @Override
        protected String getSyntax(final Number o) {
            if (o instanceof Long)
                return o + "L";
            else if (o instanceof Double)
                return o + "d";
            else if (o instanceof Float)
                return o + "f";
            else if (o instanceof Integer)
                return "(int) " + o;
            else if (o instanceof Byte)
                return "(byte) " + o;
            if (o instanceof Short)
                return "(short) " + o;
            else if (o instanceof BigInteger)
                return "new java.math.BigInteger('" + o.toString() + "')";
            else if (o instanceof BigDecimal)
                return "new java.math.BigDecimal('" + o.toString() + "')";
            else
                return o.toString();
        }

        @Override
        protected Script produceScript(final Set<?> o) {
            return produceScript(new ArrayList<>(o)).append(" as Set");
        }

        @Override
        protected Script produceScript(final List<?> o) {
            final Iterator<?> iterator = ((List<?>) o).iterator();
            script.append("[");

            while (iterator.hasNext()) {
                final Object nextItem = iterator.next();
                convertToScript(nextItem);
                if (iterator.hasNext())
                    script.append(",").append(" ");
            }

            return script.append("]");
        }

        @Override
        protected Script produceScript(final Map<?, ?> o) {
            script.append("[");
            final Iterator<? extends Map.Entry<?, ?>> itty = ((Map<?, ?>) o).entrySet().iterator();
            while (itty.hasNext()) {
                final Map.Entry<?,?> entry = itty.next();
                script.append("(");
                convertToScript(entry.getKey());
                script.append("):(");
                convertToScript(entry.getValue());
                script.append(")");
                if (itty.hasNext())
                    script.append(",");
            }
            return script.append("]");
        }

        @Override
        protected Script produceScript(final Class<?> o) {
            return script.append(o.getCanonicalName());
        }

        @Override
        protected Script produceScript(final Enum<?> o) {
            return script.append(o.getDeclaringClass().getSimpleName() + "." + o.toString());
        }

        @Override
        protected Script produceScript(final Vertex o) {
            script.append("new org.apache.tinkerpop.gremlin.structure.util.detached.DetachedVertex(");
            convertToScript(o.id());
            script.append(",");
            convertToScript(o.label());
            return script.append(", Collections.emptyMap())");
        }

        @Override
        protected Script produceScript(final Edge o) {
            script.append("new org.apache.tinkerpop.gremlin.structure.util.detached.DetachedEdge(");
            convertToScript(o.id());
            script.append(",");
            convertToScript(o.label());
            script.append(",");
            script.append("Collections.emptyMap(),");
            convertToScript(o.outVertex().id());
            script.append(",");
            convertToScript(o.outVertex().label());
            script.append(",");
            convertToScript(o.inVertex().id());
            script.append(",");
            convertToScript(o.inVertex().label());
            return script.append(")");
        }

        @Override
        protected Script produceScript(final VertexProperty<?> o) {
            script.append("new org.apache.tinkerpop.gremlin.structure.util.detached.DetachedVertexProperty(");
            convertToScript(o.id());
            script.append(",");
            convertToScript(o.label());
            script.append(",");
            convertToScript(o.value());
            script.append(",");
            script.append("Collections.emptyMap(),");
            convertToScript(o.element());
            return script.append(")");
        }

        @Override
        protected Script produceScript(final TraversalStrategyProxy<?> o) {
            if (o.getConfiguration().isEmpty()) {
                return script.append(o.getStrategyClass().getCanonicalName() + ".instance()");
            } else {
                script.append(o.getStrategyClass().getCanonicalName() + ".create(new org.apache.commons.configuration2.MapConfiguration(");
                convertToScript(ConfigurationConverter.getMap(o.getConfiguration()));
                return script.append("))");
            }
        }

        @Override
        protected Script produceScript(final String traversalSource, final Bytecode o) {
            script.append(traversalSource);
            for (final Bytecode.Instruction instruction : o.getInstructions()) {
                final String methodName = instruction.getOperator();
                if (0 == instruction.getArguments().length) {
                    script.append(".").append(methodName).append("()");
                } else {
                    script.append(".").append(methodName).append("(");

                    // have to special case withSack() for Groovy because UnaryOperator and BinaryOperator signatures
                    // make it impossible for the interpreter to figure out which function to call. specifically we need
                    // to discern between:
                    //     withSack(A initialValue, UnaryOperator<A> splitOperator)
                    //     withSack(A initialValue, BinaryOperator<A> splitOperator)
                    // and:
                    //     withSack(Supplier<A> initialValue, UnaryOperator<A> mergeOperator)
                    //     withSack(Supplier<A> initialValue, BinaryOperator<A> mergeOperator)
                    if (methodName.equals(TraversalSource.Symbols.withSack) &&
                            instruction.getArguments().length == 2 && instruction.getArguments()[1] instanceof Lambda) {
                        final String castFirstArgTo = instruction.getArguments()[0] instanceof Lambda ?
                                Supplier.class.getName() : "";
                        final Lambda secondArg = (Lambda) instruction.getArguments()[1];
                        final String castSecondArgTo = secondArg.getLambdaArguments() == 1 ? UnaryOperator.class.getName() :
                                BinaryOperator.class.getName();
                        if (!castFirstArgTo.isEmpty())
                            script.append(String.format("(%s) ", castFirstArgTo));
                        convertToScript(instruction.getArguments()[0]);
                        script.append(", (").append(castSecondArgTo).append(") ");
                        convertToScript(instruction.getArguments()[1]);
                        script.append(",");
                    } else {
                        for (final Object object : instruction.getArguments()) {
                            convertToScript(object);
                            script.append(",");
                        }
                    }
                    script.setCharAtEnd(')');
                }
            }
            return script;
        }

        @Override
        protected Script produceScript(final P<?> p) {
            if (p instanceof TextP) {
                script.append("TextP.").append(p.getBiPredicate().toString()).append("(");
                convertToScript(p.getValue());
            } else if (p instanceof ConnectiveP) {
                final List<P<?>> list = ((ConnectiveP) p).getPredicates();
                for (int i = 0; i < list.size(); i++) {
                    produceScript(list.get(i));
                    if (i < list.size() - 1) {
                        script.append(p instanceof OrP ? ".or(" : ".and(");
                    }
                }
            } else {
                script.append("P.").append(p.getBiPredicate().toString()).append("(");
                convertToScript(p.getValue());
            }
            script.append(")");
            return script;
        }
    }
}
