package com.parchie.web;

import com.parchie.exception.InvalidCredentialsException;
import com.parchie.model.User;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUser.class)
                && parameter.getParameterType().equals(User.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) return null;
        User user = CurrentUser.from(request.getAttribute(CurrentUser.REQUEST_ATTR));
        AuthenticatedUser annotation = parameter.getParameterAnnotation(AuthenticatedUser.class);
        if (user == null && annotation != null && annotation.required()) {
            // 401 — handled by ExceptionHandler/@ResponseStatus.
            throw new InvalidCredentialsException("Authentication required");
        }
        return user;
    }
}
