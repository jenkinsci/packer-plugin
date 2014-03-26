/**
 * Copyright 2000-2013 NeuStar, Inc. All rights reserved.
 * NeuStar, the Neustar logo and related names and logos are registered
 * trademarks, service marks or tradenames of NeuStar, Inc. All other
 * product names, company names, marks, logos and symbols may be trademarks
 * of their respective owners.
 */

package biz.neustar.jenkins.plugins.packer;

public enum TemplateMode {
    TEXT("text"),
    FILE("file"),
    GLOBAL("global");

    private final String mode;
    TemplateMode(String mode) {
        this.mode = mode;
    }
    public String toMode() {
        return mode;
    }
    public boolean isMode(String mode) {
        return mode != null && this.mode.equals(mode);
    }
}
