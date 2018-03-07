/**
 * Copyright 2000-2014 NeuStar, Inc. All rights reserved.
 * NeuStar, the Neustar logo and related names and logos are registered
 * trademarks, service marks or tradenames of NeuStar, Inc. All other
 * product names, company names, marks, logos and symbols may be trademarks
 * of their respective owners.
 */

package biz.neustar.jenkins.plugins.packer;

import com.google.common.base.Strings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 */
public class PackerInstallation extends ToolInstallation implements
        EnvironmentSpecific<PackerInstallation>,
        NodeSpecific<PackerInstallation>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(PackerInstallation.class.getName());
    public static final String UNIX_PACKER_COMMAND = "packer";
    public static final String WINDOWS_PACKER_COMMAND = "packer.exe";

    private final String packerHome;
    private final String params;
    private final String jsonTemplate;
    private final String jsonTemplateText;
    private final String templateMode;
    private List<PackerFileEntry> fileEntries = Collections.emptyList();

    @DataBoundConstructor
    public PackerInstallation(String name, String home, String params,
                              JSONObject templateMode,
                              List<PackerFileEntry> fileEntries,
                              List<? extends ToolProperty<?>> properties) {
        this(name, launderHome(home), params,
             templateMode.optString("jsonTemplate", null),
             templateMode.optString("jsonTemplateText", null),
             Strings.isNullOrEmpty(templateMode.optString("value", null)) ? TemplateMode.TEXT.toMode() : templateMode.getString("value"),
             fileEntries, properties);
    }

    private PackerInstallation(String name, String home, String params,
                               String jsonTemplate, String jsonTemplateText, String templateMode,
                               List<PackerFileEntry> fileEntries,
                              List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
        this.packerHome = super.getHome();
        this.params = params;
        this.fileEntries = fileEntries;
        this.jsonTemplate = jsonTemplate;
        this.jsonTemplateText = jsonTemplateText;
        this.templateMode = templateMode;
    }

    private static String launderHome(String home) {
        if (home.endsWith("/") || home.endsWith("\\")) {
            // see https://issues.apache.org/bugzilla/show_bug.cgi?id=26947
            // Ant doesn't like the trailing slash, especially on Windows
            return home.substring(0, home.length() - 1);
        } else {
            return home;
        }
    }

    @Override
    public String getHome() {
        if (packerHome != null) {
            return packerHome;
        }
        return super.getHome();
    }

    public List<PackerFileEntry> getFileEntries() {
        if (fileEntries == null) {
            return Collections.emptyList();
        }
        return fileEntries;
    }

    public String getParams() {
        return params;
    }

    public String getJsonTemplate() {
        return jsonTemplate;
    }

    public String getJsonTemplateText() {
        return jsonTemplateText;
    }

    public String getTemplateMode() {
        return templateMode;
    }

    public boolean isFileTemplate() {
        return TemplateMode.FILE.isMode(templateMode);
    }

    public boolean isTextTemplate() {
        return TemplateMode.TEXT.isMode(templateMode);
    }

    public PackerInstallation forEnvironment(EnvVars environment) {
        return new PackerInstallation(getName(),
                environment.expand(packerHome), params, jsonTemplate, jsonTemplateText, templateMode,
                fileEntries,
                getProperties().toList());
    }

    public PackerInstallation forNode(Node node, TaskListener log)
            throws IOException, InterruptedException {
        return new PackerInstallation(getName(), translateFor(node, log),
                params, jsonTemplate, jsonTemplateText, templateMode, fileEntries, getProperties().toList());
    }

    public String getExecutable(Launcher launcher) throws InterruptedException, IOException {
        return launcher.getChannel().call(new GetExecutable((packerHome)));
    }

    private static class GetExecutable extends MasterToSlaveCallable<String, IOException> {
        private final String packerHome;
        GetExecutable(String packerHome) {
            this.packerHome = packerHome;
        }
        @Override
        public String call() throws IOException {
            File exe = getExeFile(packerHome);
            if (exe.exists()) {
                return exe.getPath();
            }
            return null;
        }
    }

    private static File getExeFile(String packerHome) {
        String execName = (Functions.isWindows()) ? WINDOWS_PACKER_COMMAND : UNIX_PACKER_COMMAND;
        String home = Util.replaceMacro(packerHome, EnvVars.masterEnvVars);
        return new File(home, execName);
    }

    protected File getExeFile() {
        return getExeFile(packerHome);
    }

    @Extension
    public static class DescriptorImpl extends
            ToolDescriptor<PackerInstallation> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Packer";
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new PackerInstaller(null));
        }

        @Override
        public PackerInstallation[] getInstallations() {
            return Jenkins.getInstance()
                    .getDescriptorByType(PackerPublisher.DescriptorImpl.class)
                    .getInstallations();
        }

        @Override
        public void setInstallations(PackerInstallation... installations) {
            Jenkins.getInstance()
                    .getDescriptorByType(PackerPublisher.DescriptorImpl.class)
                    .setInstallations(installations);
        }

        public boolean isTextTemplateChecked(PackerInstallation installation) {
            if (installation == null) {
                return true;
            }
            return installation.isTextTemplate() || !installation.isFileTemplate();
        }
    }

}
