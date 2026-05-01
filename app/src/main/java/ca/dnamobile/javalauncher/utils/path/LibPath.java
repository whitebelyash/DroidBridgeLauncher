package ca.dnamobile.javalauncher.utils.path;

import java.io.File;

public final class LibPath {
    private LibPath() {
    }

    private static File COMPONENTS_DIR;
    private static File OTHER_LOGIN_DIR;

    public static File CACIO_8;
    public static File CACIO_17;
    public static File CACIO_17_AGENT;

    public static File FORGE_INSTALLER;
    public static File MIO_FABRIC_AGENT;

    public static File MIO_LIB_PATCHER;
    public static File OPTIFINE_RENAMER;

    public static File AUTHLIB_INJECTOR;
    public static File NIDE_8_AUTH;

    public static File JAVA_SANDBOX_POLICY;
    public static File LOG4J_XML_1_7;
    public static File LOG4J_XML_1_12;
    public static File PRO_GRADE;

    public static void refresh() {
        COMPONENTS_DIR = new File(PathManager.DIR_DATA, "components");
        OTHER_LOGIN_DIR = new File(PathManager.DIR_GAME_HOME, "other_login");

        CACIO_8 = new File(PathManager.DIR_GAME_HOME, "caciocavallo");
        CACIO_17 = new File(PathManager.DIR_GAME_HOME, "caciocavallo17");
        CACIO_17_AGENT = new File(CACIO_17, "cacio-agent.jar");

        FORGE_INSTALLER = new File(COMPONENTS_DIR, "forge_installer.jar");
        MIO_FABRIC_AGENT = new File(COMPONENTS_DIR, "MioFabricAgent.jar");

        MIO_LIB_PATCHER = new File(COMPONENTS_DIR, "MioLibPatcher.jar");
        OPTIFINE_RENAMER = new File(COMPONENTS_DIR, "OptiFineRenamer.jar");

        AUTHLIB_INJECTOR = new File(OTHER_LOGIN_DIR, "authlib-injector.jar");
        NIDE_8_AUTH = new File(OTHER_LOGIN_DIR, "nide8auth.jar");

        JAVA_SANDBOX_POLICY = new File(COMPONENTS_DIR, "java_sandbox.policy");
        LOG4J_XML_1_7 = new File(COMPONENTS_DIR, "log4j-rce-patch-1.7.xml");
        LOG4J_XML_1_12 = new File(COMPONENTS_DIR, "log4j-rce-patch-1.12.xml");
        PRO_GRADE = new File(COMPONENTS_DIR, "pro-grade.jar");
    }

    static {
        refresh();
    }
}
