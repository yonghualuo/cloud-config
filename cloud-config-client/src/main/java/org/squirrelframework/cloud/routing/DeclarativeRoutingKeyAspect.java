package org.squirrelframework.cloud.routing;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.Ordered;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringValueResolver;
import org.squirrelframework.cloud.annotation.RoutingKey;
import org.squirrelframework.cloud.annotation.RoutingVariable;
import org.squirrelframework.cloud.utils.CloudConfigCommon;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.squirrelframework.cloud.routing.RoutingKeyHolder.*;

/**
 * Created by kailianghe on 15/12/9.
 */
@Aspect
public class DeclarativeRoutingKeyAspect implements Ordered, EmbeddedValueResolverAware {

    private static Pattern pattern = Pattern.compile("^#\\s*\\{\\s*(.+?)\\s*\\}$");

    private LoadingCache<String, Expression> expressionCache = CacheBuilder.newBuilder()
            .weakKeys()
            .build(CloudConfigCommon.EL_EXPRESSION_LOADER);

    private StringValueResolver stringValueResolver;

    @Around(value = "@annotation(routingKey)")
    public Object process(ProceedingJoinPoint jp, RoutingKey routingKey) throws Throwable {
        boolean routingEntryFlag = isRoutingEntry();
        try {
            if(routingEntryFlag) {
                setRoutingEntry(false);
                if(routingKey.recordRoutingKeys()) {
                    setRoutingKeyTraceEnabled(true);
                }
            }
            for(String toBeResolved : routingKey.value()) {
                String resolvedValue = resolveRoutingValue(jp, toBeResolved);
                putDeclarativeRoutingKey(resolvedValue);
            }
            return jp.proceed();
        } finally {
            if(routingEntryFlag) {
                removeRoutingEntry();
                if(routingKey.recordRoutingKeys()) {
                    removeRoutingKeyTraceEnabled();
                    removeRoutingKeys();
                }
                removeDeclarativeRoutingKeys();
            }
        }
    }

    private String resolveRoutingValue(ProceedingJoinPoint jp, String routingValue) throws Exception {
        String resolvedValue = stringValueResolver.resolveStringValue(routingValue).trim();
        Matcher matcher = pattern.matcher(resolvedValue);
        if(matcher.find()) {
            // prepare execution context
            StandardEvaluationContext simpleContext = new StandardEvaluationContext();
            simpleContext.setRootObject(jp.getTarget()); // or getThis?
            simpleContext.setVariable("args", jp.getArgs());

            MethodSignature signature = (MethodSignature) jp.getSignature();
            Method method = signature.getMethod();
            Annotation[][] annotations = method.getParameterAnnotations();
            for(int i=0; i<annotations.length; i++) {
                if(annotations[i]==null || annotations[i].length==0) continue;
                RoutingVariable routingParam = null;
                for(Annotation annotation : annotations[i]) {
                    if(annotation.annotationType() == RoutingVariable.class) {
                        routingParam = (RoutingVariable) annotation;
                        break;
                    }
                }
                if(routingParam!=null) {
                    simpleContext.setVariable(routingParam.value(), jp.getArgs()[i]);
                }
            }
            String elExpr = matcher.group(1);
            resolvedValue = expressionCache.get(elExpr).getValue(simpleContext, String.class);
        }
        return resolvedValue;
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.stringValueResolver = resolver;
    }
}
