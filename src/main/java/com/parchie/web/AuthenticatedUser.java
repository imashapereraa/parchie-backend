package com.parchie.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// mark a controller parameter to receive the authenticated User. If `required`
// is true (default) the request is rejected with 401 when no valid token was
// supplied; if false, the resolver injects null.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface AuthenticatedUser {
    boolean required() default true;
}
