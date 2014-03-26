/**
 * Copyright 2000-2014 NeuStar, Inc. All rights reserved.
 * NeuStar, the Neustar logo and related names and logos are registered
 * trademarks, service marks or tradenames of NeuStar, Inc. All other
 * product names, company names, marks, logos and symbols may be trademarks
 * of their respective owners.
 */

package biz.neustar.jenkins.packer;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import java.io.IOException;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;


public class PackerInstaller extends DownloadFromUrlInstaller {
    private static final Logger LOGGER = Logger.getLogger(PackerInstaller.class.getName());

    @DataBoundConstructor
    public PackerInstaller(String id) {
        super(id);
    }

    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log)
            throws IOException, InterruptedException {
        LOGGER.info("Performing Install");
        return super.performInstallation(tool, node, log);
    }

    @Extension
    public static final class DescriptorImpl extends
            DownloadFromUrlInstaller.DescriptorImpl<PackerInstaller> {
        public String getDisplayName() {
            return "Install from Packer site";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == PackerInstallation.class;
        }
    }
}
