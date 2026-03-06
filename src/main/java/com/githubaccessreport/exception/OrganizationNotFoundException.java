package com.githubaccessreport.exception;

public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(String organization) {
        super("GitHub organization not found: " + organization);
    }
}
