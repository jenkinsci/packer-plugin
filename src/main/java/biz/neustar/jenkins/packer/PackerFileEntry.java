/**
 * Copyright 2000-2013 NeuStar, Inc. All rights reserved.
 * NeuStar, the Neustar logo and related names and logos are registered
 * trademarks, service marks or tradenames of NeuStar, Inc. All other
 * product names, company names, marks, logos and symbols may be trademarks
 * of their respective owners.
 */

package biz.neustar.jenkins.packer;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

public class PackerFileEntry extends AbstractDescribableImpl<PackerFileEntry> implements Serializable {

    private static final long serialVersionUID = 1L;
    private String varFileName;
    private String contents;

    @DataBoundConstructor
    public PackerFileEntry(String varFileName, String contents) {
        this.varFileName = varFileName;
        this.contents = contents;
    }

    public String getVarFileName() {
        return varFileName;
    }

    public void setVarFileName(String varFileName) {
        this.varFileName = varFileName;
    }

    public String getContents() {
        return contents;
    }

    public void setContents(String contents) {
        this.contents = contents;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PackerFileEntry> {
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
