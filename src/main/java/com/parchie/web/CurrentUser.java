package com.parchie.web;

import com.parchie.model.User;

/**
 * Holds the authenticated user for the lifetime of one request. Populated by
 * {@link AuthTokenFilter} from the Authorization header; controllers receive
 * it via {@link CurrentUserArgumentResolver} on parameters annotated with
 * {@code @AuthenticatedUser}.
 */
public final class CurrentUser {

    public static final String REQUEST_ATTR = "currentUser";

    private CurrentUser() {}

    public static User from(Object attr) {
        return attr instanceof User u ? u : null;
    }
}
