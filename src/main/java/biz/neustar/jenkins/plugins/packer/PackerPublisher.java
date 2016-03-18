/**
 * Copyright 2000-2014 NeuStar, Inc. All rights reserved.
 * NeuStar, the Neustar logo and related names and logos are registered
 * trademarks, service marks or tradenames of NeuStar, Inc. All other
 * product names, company names, marks, logos and symbols may be trademarks
 * of their respective owners.
 */

package biz.neustar.jenkins.plugins.packer;

import hudson.AbortException;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ArgumentListBuilder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;


/**
 * Publishing task that performs a call to a Packer executable
 * See: <a href="http://packer.io">Packer</a>
 *
 */
public class PackerPublisher extends Recorder {
    private static final Logger LOGGER = Logger.getLogger(PackerPublisher.class.getName());

    public static final String TEMPLATE_MODE = "templateMode";

    private final String name;
    private String jsonTemplate = "";
    private String jsonTemplateText;
    private String packerHome = "";
    private String params = "";
    private final boolean useDebug;
    private final String changeDir;
    private String templateMode = TemplateMode.GLOBAL.toMode();
    private List<PackerFileEntry> fileEntries = Collections.emptyList();

    @DataBoundConstructor
    public PackerPublisher(String name,
                           String jsonTemplate,
                           String jsonTemplateText,
                           String packerHome,
                           String params,
                           List<PackerFileEntry> fileEntries,
                           boolean useDebug,
                           String changeDir) {

        this.name = name;
        this.jsonTemplate = jsonTemplate;
        this.jsonTemplateText = jsonTemplateText;
        this.packerHome = packerHome;
        this.params = params;
        this.fileEntries = fileEntries;
        this.useDebug = useDebug;
        this.changeDir = changeDir;
    }

    public String getName() {
        return getInstallation().getName();
    }

    public String getPackerHome() {
        return packerHome;
    }

    public void setPackerHome(String packerHome) {
        this.packerHome = packerHome;
    }


    public List<PackerFileEntry> getFileEntries() {
        if (fileEntries == null) {
            return Collections.emptyList();
        }
        return fileEntries;
    }

    public void setFileEntries(List<PackerFileEntry> fileEntries) {
        this.fileEntries = fileEntries;
    }

    public String getJsonTemplate() {
        return jsonTemplate;
    }

    public void setJsonTemplate(String jsonTemplate) {
        this.jsonTemplate = jsonTemplate;
    }


    public String getJsonTemplateText() {
        return jsonTemplateText;
    }

    public void setJsonTemplateText(String jsonTemplateText) {
        this.jsonTemplateText = jsonTemplateText;
    }

    // This method is for output of user-friendly text only.
    public String getGlobalTemplate() {
        PackerInstallation installation = getInstallation();
        if (installation.isTextTemplate()) {
            return installation.getJsonTemplateText();
        }
        return "Using File: " + installation.getJsonTemplate();
    }

    public static String createJsonTemplateTextTempFile(FilePath workspacePath, String contents) throws AbortException {
        try {
            LOGGER.info("jsonTemplateText: " + contents);
            if (Util.fixEmpty(contents) != null) {
                FilePath jsonFile = workspacePath.createTextTempFile("packer", ".json", contents, false);
                LOGGER.info("Using temp file: " + jsonFile.getRemote());
                return jsonFile.getRemote();
            }
        } catch (IOException ioe) {
            LOGGER.warning(convertException(ioe));
        } catch (InterruptedException inte) {
            LOGGER.warning(convertException(inte));
        }
        throw new AbortException("Template Generation / Loading Failed");
    }

    public String createJsonTemplateTextTempFile(FilePath workspacePath) throws AbortException {
        return createJsonTemplateTextTempFile(workspacePath, jsonTemplateText);
    }


    public String getTemplateMode() {
        return templateMode;
    }

    // TODO: @DataBoundSetter
    public void setTemplateMode(String templateMode) {
        this.templateMode = templateMode;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public boolean getUseDebug() {
        return useDebug;
    }

    public String getChangeDir() {
        return this.changeDir;
    }


    public boolean isFileTemplate() {
        return TemplateMode.FILE.isMode(templateMode);
    }

    public boolean isTextTemplate() {
        return TemplateMode.TEXT.isMode(templateMode);
    }

    public boolean isGlobalTemplate() {
        return TemplateMode.GLOBAL.isMode(templateMode);
    }

    public boolean isGlobalTemplateChecked() {
        return isGlobalTemplate() || (!isFileTemplate() && !isTextTemplate());
    }


    public PackerInstallation getInstallation() {
        for (PackerInstallation install : getDescriptor().getInstallations()) {
            if (name != null && install.getName().equals(name)) {
                return install;
            }
        }

        return null;
    }

    // in Windows packer installation has packer.exe is located in packer_home
    // Windows exec file is packer.exe (will refer to as "packer")
    public String getRemotePackerExec(AbstractBuild build, Launcher launcher,
                                      TaskListener listener) throws AbortException {

        String home = getPackerHome();
        String remoteExec = null;
        if (Util.fixEmpty(home) == null) {
            PackerInstallation install = getInstallation();
            try {
                install = install.forNode(build.getBuiltOn(), listener)
                        .forEnvironment(build.getEnvironment(listener));

                remoteExec = install.getExecutable(launcher);
            } catch (Exception ex) {
                LOGGER.severe(convertException(ex));
                throw new AbortException("Tool Installation Failed for: " + getName());
            }
        } else {
            FilePath execPath = getRemotePath(build, home);
            if (!home.toLowerCase().endsWith(PackerInstallation.WINDOWS_PACKER_COMMAND)) {
                execPath = new FilePath(execPath, isFilePathUnix(execPath) ?
                        PackerInstallation.UNIX_PACKER_COMMAND  :
                        PackerInstallation.WINDOWS_PACKER_COMMAND);
            }
            remoteExec = execPath.getRemote();
        }
        LOGGER.info("Using packer: " + remoteExec);
        return remoteExec;
    }

    public String getRemoteTemplate(AbstractBuild build, String... remotePaths) {
        FilePath templatePath = getRemotePath(build, remotePaths);
        LOGGER.info("Using templatePath: " + templatePath);
        return templatePath.getRemote();
    }

    // either absolute or relative to project workspace
    public FilePath getRemotePath(AbstractBuild build, String... remotePaths) {
        FilePath result = build.getWorkspace();
        for (String remotePath : remotePaths) {
            String path = remotePath.trim();
            if (!path.isEmpty()) {
                result = new FilePath(result, path);
            }
        }
        return result;
    }


    /**
     * Create the temporary files from the configured entries.
     * @return the cmd line variable value for those entries.
     */
    public String createTempFileEntries(FilePath workspacePath) throws AbortException {
        StringBuilder variables = new StringBuilder();
        PackerInstallation install = getInstallation();
        HashMap<String, PackerFileEntry> fileEntries = new HashMap<>();
        if (install != null) {
            for (PackerFileEntry entry : install.getFileEntries()) {
                fileEntries.put(entry.getVarFileName(), entry);
            }
        }
        try {
            // potentially replace a global, which is what we want.
            for (PackerFileEntry entry : getFileEntries()) {
                fileEntries.put(entry.getVarFileName(), entry);
            }

            for (PackerFileEntry entry : fileEntries.values()) {
                FilePath entryFile = workspacePath.createTextTempFile(entry.getVarFileName(), ".tmp",
                            entry.getContents(), false);
                variables.append(String.format("-var \"%s=%s\" ", entry.getVarFileName(), entryFile.getRemote()));
            }

        } catch (IOException e) {
            LOGGER.severe(convertException(e));
            throw new AbortException("File Entry Generation Failed");
        } catch (InterruptedException e) {
            LOGGER.severe(convertException(e));
            throw new AbortException("File Entry Generation Failed");
        }
        return variables.toString();
    }



    @Override
    public boolean perform(AbstractBuild build, Launcher launcher,
                           BuildListener listener) {
        ArgumentListBuilder args = new ArgumentListBuilder();
        try {
            args.add(getRemotePackerExec(build, launcher, listener)).add("build");

            EnvVars env = build.getEnvironment(listener);

            PackerInstallation installation = getInstallation();

            // mask the global params.
            for (String param : addParamsAsArgs(Util.fixNull(installation.getParams()))) {
                String addParam = param.trim();
                if (addParam.length() > 0) {
                    args.add(Util.replaceMacro(addParam, env), true);
                }
            }

            for (String param : addParamsAsArgs(getParams())) {
                String addParam = param.trim();
                if (addParam.length() > 0) {
                    args.add(Util.replaceMacro(addParam, env));
                }
            }

            for (String val : addParamsAsArgs(createTempFileEntries(build.getWorkspace()))) {
                args.add(val);
            }

            if (getUseDebug()) {
                args.add("-debug");
            }

            FilePath workingDir = workingDir(build, env);
            LOGGER.info("using working dir: " + workingDir);

            if (isGlobalTemplate()) {
                LOGGER.info("Using GlobalTemplate");
                if (installation.isFileTemplate()) {
                    args.add(getRemoteTemplate(build, Util.replaceMacro(getChangeDir(), env),
                                Util.replaceMacro(installation.getJsonTemplate(), env)));
                } else {
                    args.add(createJsonTemplateTextTempFile(workingDir, installation.getJsonTemplateText()));
                }
            } else if (isTextTemplate()) {
                LOGGER.info("Using TextTemplate");
                args.add(createJsonTemplateTextTempFile(workingDir));
            } else if (isFileTemplate()) {
                LOGGER.info("Using FileTemplate");
                args.add(getRemoteTemplate(build, Util.replaceMacro(getChangeDir(), env),
                            Util.replaceMacro(getJsonTemplate(), env)));
            } else { // throw
                LOGGER.warning("Unknown Template");
                throw new AbortException("Unknown Template / Loading Failed");
            }

            try {
                LOGGER.info("launch: " + args.toString());
                if (launcher.launch().pwd(workingDir).cmds(args).stdout(listener).join() == 0) {
                    listener.finished(Result.SUCCESS);
                    // parse the log to look for the image id

                    return true;
                }
            } catch (Exception ex) {
                LOGGER.severe(convertException(ex));
                listener.fatalError("Execution failed: " + args);
            }
        } catch (Exception e) {
            LOGGER.severe(convertException(e));
            listener.fatalError("Execution failed: " + args);
        }
        listener.finished(Result.FAILURE);
        return false;
    }


    protected FilePath workingDir(AbstractBuild build, EnvVars env) {
        if (Util.fixEmpty(getChangeDir()) != null) {
            return new FilePath(build.getWorkspace().getChannel(), Util.replaceMacro(getChangeDir(),env));
        }
        return build.getWorkspace();
    }

    protected static String convertException(Exception ex) {
        StringWriter stringWriter = new StringWriter();
        ex.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }


    // Adapted from FilePath since this method is not public
    public static boolean isFilePathUnix(FilePath path) {
        if(!path.isRemote()) {
            return File.pathSeparatorChar != ';';
        }

        String remote = path.getRemote();
        // Windows absolute path is 'X:\...', so this is usually a good indication of Windows path
        if(remote.length() > 3 && remote.charAt(1) == ':' && remote.charAt(2) == '\\') {
            return false;
        }
        return remote.indexOf("\\") == -1;
    }

    public static List<String> addParamsAsArgs(String params) {
        List<String> args = new ArrayList<>();
        if (params == null || params.isEmpty()) {
            return args;
        }

        char[] chars = params.toCharArray();
        int captureIndex = -1;
        char quoteChar = '\0';
        boolean inQuote = false;
        boolean stripQuotes = false;
        for (int index = 0; index < chars.length; index++) {
            char c = chars[index];

            if (inQuote && c == quoteChar) { // finished
                inQuote = false;
            } else if (captureIndex > -1 && !inQuote && c == ' ') {
                int start = stripQuotes ? captureIndex + 1 : captureIndex;
                int stop = stripQuotes ? index - 1 : index;
                args.add(params.substring(start, stop));
                captureIndex = -1;
                stripQuotes = false;
            } else if (captureIndex == -1 && !inQuote && c != ' ') {
                captureIndex = index;
                if (c == '\'' || c == '\"') {
                    inQuote = true;
                    quoteChar = c;
                    stripQuotes = true;
                }
            }
        }
        if (captureIndex > -1) {
            int start = stripQuotes ? captureIndex + 1 : captureIndex;
            int stop = stripQuotes ? params.length() - 1 : params.length();
            args.add(params.substring(start, stop));
        }
        return args;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }


    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public PackerPublisher newInstance(StaplerRequest req, JSONObject formData)
                throws hudson.model.Descriptor.FormException {

            PackerPublisher packer = (PackerPublisher) req.bindJSON(clazz,
                    formData);

            if (formData.has(TEMPLATE_MODE)) {
                JSONObject opt = formData.getJSONObject(TEMPLATE_MODE);
                packer.setTemplateMode(opt.getString("value"));
                packer.setJsonTemplate(opt.optString("jsonTemplate"));
                packer.setJsonTemplateText(opt.optString("jsonTemplateText"));
            }
            return packer;
        }

        @CopyOnWrite
        private volatile PackerInstallation[] installations = new PackerInstallation[0];

        public PackerInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(PackerInstallation... installations) {
            this.installations = installations;
            save();
        }

        public boolean isGlobalTemplateChecked(PackerPublisher instance) {
            boolean result = true;
            if (instance != null) {
                result = instance.isGlobalTemplateChecked();
            }
            return result;
        }

        public String getGlobalTemplate(PackerPublisher instance) {
            String result = "Save and reload to see global template...";
            if (instance != null) {
                result = instance.getGlobalTemplate();
            }
            return result;
        }

        /**
         * In order to load the persisted global configuration, you have to call
         * load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Packer";
        }

    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

}
