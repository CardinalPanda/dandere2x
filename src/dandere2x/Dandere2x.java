package dandere2x;

import dandere2x.Utilities.Dandere2xCUI;
import dandere2x.Utilities.DandereUtils;
import dandere2x.Utilities.ParseConfig;
import wrappers.FFMpeg;
import wrappers.Waifu2xCaffe;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;

import static java.io.File.separator;
import static java.lang.System.exit;
import static java.lang.System.out;

public class Dandere2x {

    //directories
    private String dandereDir;
    private String workspace;
    private String fileDir;
    private String timeFrame;
    private String duration;
    private String audioLayer;
    private String dandere2xCppDir;
    private String waifu2xCaffeCUIDir;
    private String fileLocation;
    private String outLocation;
    private String upscaledLocation;
    private String mergedDir;
    private String inversion_dataDir;
    private String pframe_dataDir;
    private String debugDir;
    private String logDir;
    private String ffmpegDir;

    //custom but relevent settings
    private String noiseLevel;
    private String processType;
    private int frameRate;
    private int frameCount;
    private int blockSize;
    private int stepSize;
    private int bleed;
    private double tolerance;


    //session stuff
    private Properties prop;
    private Process dandere2xCppProc;
    private PrintStream log;


    /**
     *
     */
    public Dandere2x(ParseConfig config) {
        if (!config.verify()) {
            System.out.println("Invalid config");
            exit(1);
        }

        this.prop = config.getProp();
        this.assignProperties();
    }

    public void start() throws IOException, InterruptedException {

        createDirs();
        try {
            log = new PrintStream(new File(workspace + "logs" + separator + "dandere2x_logfile.txt"));
        } catch (FileNotFoundException e) {
            System.out.println("Fatal Error: Could not create file at " + workspace + "logs" + separator + "dandere2x_logfile.txt");
            exit(1);
        }

        frameCount = DandereUtils.getSecondsFromDuration(duration) * frameRate;


        //if the frames have already been extracted, don't do it again
        if((!new File(workspace + "inputs").exists()) || DandereUtils.getFileTypeInFolder(workspace + "inputs",".jpg").isEmpty()){
            newRun();
        }
        else{
            System.out.println("Resuming session");
            log.println("Resuming session");

            System.out.println("difference frames counted: " +
                    DandereUtils.getFileTypeInFolder(workspace + "outputs" + separator,".jpg").size());
            System.out.println("merged frames counted: " +
                    DandereUtils.getFileTypeInFolder(workspace + "merged" + separator,".jpg").size());

            log.println("difference frames counted: " +
                    DandereUtils.getFileTypeInFolder(workspace + "outputs" + separator,".jpg").size());
            log.println("merged frames counted: " +
                    DandereUtils.getFileTypeInFolder(workspace + "merged" + separator,".jpg").size());

        }

        log.println("framecount: " + frameCount);

        printDandereSession();

        log.println("calling initial setup");
        initialSetup();

        log.println("initiating shutdown hook");
        shutdownHook();

        log.println("starting threaded processes");
        startThreadedProcesses();
    }

    public void newRun() throws IOException, InterruptedException{
        log.println("extracting frames");
        FFMpeg.extractFrames(log, ffmpegDir, workspace, timeFrame, fileDir, duration);

        log.println("extracting audio");
        FFMpeg.extractAudio(log, ffmpegDir, workspace, timeFrame, duration, fileDir, audioLayer);
    }

    public void resume() throws IOException, InterruptedException {

    }


    private void assignProperties() {

        //directories
        this.dandereDir = this.prop.getProperty("dandereDir");
        this.workspace = this.prop.getProperty("workspace");
        this.fileDir = this.prop.getProperty("fileDir");
        this.dandere2xCppDir = this.prop.getProperty("dandere2xCppDir");
        this.waifu2xCaffeCUIDir = this.prop.getProperty("waifu2xCaffeCUIDir");
        this.ffmpegDir = prop.getProperty("ffmpegDir");

        //session settings
        this.timeFrame = this.prop.getProperty("timeFrame");
        this.duration = this.prop.getProperty("duration");
        this.audioLayer = this.prop.getProperty("audioLayer");
        this.frameRate = Integer.parseInt(this.prop.getProperty("frameRate"));


        //custom settings
        this.stepSize = Integer.parseInt(this.prop.getProperty("stepSize"));
        this.blockSize = Integer.parseInt(this.prop.getProperty("blockSize"));
        this.tolerance = Double.parseDouble(this.prop.getProperty("tolerance"));
        this.noiseLevel = this.prop.getProperty("noiseLevel");
        this.processType = this.prop.getProperty("processType");
        this.bleed = Integer.parseInt(this.prop.getProperty("bleed"));

    }

    //if program exits and dandere2xCppProc is still running, close that up
    private void shutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                if (dandere2xCppProc.isAlive()) {
                    System.out.println("Unexpected shutting down before dandere2xCppProc finished! Attempting to close");
                    log.println("Unexpected shutting down before dandere2xCppProc finished! Attempting to close");
                    dandere2xCppProc.destroyForcibly();
                    System.out.println("Exiting..");
                    log.println("Exiting..");
                }
            }
        });
    }


    //setup folders in workspace
    private void createDirs() {
        fileLocation = workspace + "inputs" + separator;
        outLocation = workspace + "outputs" + separator;
        upscaledLocation = workspace + "upscaled" + separator;
        mergedDir = workspace + "merged" + separator;
        inversion_dataDir = workspace + "inversion_data" + separator;
        pframe_dataDir = workspace + "pframe_data" + separator;
        debugDir = workspace + "debug" + separator;
        logDir = workspace + "logs" + separator;

        if (!new File(workspace).exists())
            new File(workspace).mkdir();


        new File(outLocation).mkdir();
        new File(upscaledLocation).mkdir();
        new File(mergedDir).mkdir();
        new File(inversion_dataDir).mkdir();
        new File(pframe_dataDir).mkdir();
        new File(debugDir).mkdir();
        new File(logDir).mkdir();

    }


    //prints the dandere session for debugging
    //https://alvinalexander.com/blog/post/java/print-all-java-system-properties
    public void printDandereSession() {
        Enumeration keys = prop.keys();
        System.out.println("DANDERE 2x Session Properties");
        System.out.println("----------");
        out.println("----------");
        log.println("DANDERE 2x Session Properties");
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = (String) prop.get(key);
            System.out.println(key + ": " + value);
            log.println(key + ": " + value);
        }
        out.println("----------");
        System.out.println("----------");
    }


    /*
    A multi platform process. This is the only real differing factor between linux and windows
    version of Dandere2x - waifu2x-caffee needs to be invoked for windows, while we use normal waifu2x (dandere.lua to be
    exact) on linux.

    The linux version creates an runnable .sh file that is to be called during runtime. This process
    is not required on windows.

    Threaded processes include:

    1) Dandere2xCpp, which matches blocks between frames
    2) Difference, which creates an image based off information from Dandere2xCpp
    3) Merge, which merges upscaled images to complete frames
    4) Waifu2xCaffee - A process to upscale frames on windows.
     */
    private void startThreadedProcesses() throws IOException, InterruptedException {

        log.println("starting threaded processes...");


        ProcessBuilder dandere2xPB = getDandere2xPB();
        dandere2xCppProc = dandere2xPB.start();

        Thread dandereCUI = new Thread() {
            public void run() {
                Dandere2xCUI cui = new Dandere2xCUI(mergedDir, frameCount);
                cui.run();
            }
        };

        Thread inversionThread = new Thread() {
            public void run() {
                Difference inv = new Difference(blockSize, 2, workspace, frameCount);
                inv.run();
            }
        };

        Thread mergeThread = new Thread() {
            public void run() {
                Merge dif = new Merge(blockSize, 2, workspace, frameCount);
                dif.run();
            }
        };

        log.println("Starting cui thread");
        dandereCUI.start();
        log.println("starting merge thread...");
        mergeThread.start();
        log.println("starting inversion thread...");
        inversionThread.start();


        /**
         * If we're on windows, use the waifu2x-caffe wrapper, upscale the first scene,
         * then start a process to continiously upscale images using waifu2x-caffee. Then, wait for this process
         * to finish.
         */
        if (!DandereUtils.isLinux()) {
            log.println("upscaling frame1");

            //manually upscale first frame
            Waifu2xCaffe.upscaleFile(waifu2xCaffeCUIDir, fileLocation + "frame1.jpg",
                    mergedDir + "merged_" + 1 + ".jpg", processType, noiseLevel, "2");

            //start the process of upscaling every inversion
            log.println("starting waifu2x caffee upscaling...");
            Waifu2xCaffe waifu = new Waifu2xCaffe(workspace, waifu2xCaffeCUIDir, outLocation, upscaledLocation, frameCount, processType, noiseLevel, "2");
            Thread waifuxThread = new Thread() {
                public void run() {
                    waifu.upscale();
                }
            };
            waifuxThread.start();
            waifuxThread.join();
        }

        inversionThread.join();
        mergeThread.join();
        while (dandere2xCppProc.isAlive()) ;
        log.println("dandere2x finished correctly...");
    }



    /**
     * IF we're on linux, create the script for the user to run. The process builder command
     * to start waifu2x-cpp is also different than that of windows.
     */
    private ProcessBuilder getDandere2xPB(){

        int count = DandereUtils.getFileTypeInFolder(workspace + "pframe_data",".txt").size();

        String type = null;
        if(count == 0){
            log.print("New Dandere2x Session");
            type = "n";
        }
        else{
            new File(workspace + "pframe_data" + separator + "pframe_"+count).delete();
            new File(workspace + "inversion_data" + separator + "inversion_"+count).delete();
            log.println("Resuming Dandere2x Session: " + count);
            type = "r";
        }


        ProcessBuilder dandere2xPB;

        if (DandereUtils.isLinux()) {
            log.println("using linux...");
            dandere2xPB = new ProcessBuilder(dandere2xCppDir,
                    workspace, frameCount + "", blockSize + "", tolerance + "", stepSize + "", type,count + "");
            createWaifu2xScript();
        } else {
            log.println("using windows...");
            dandere2xPB = new ProcessBuilder("cmd.exe", "/C", "start", dandere2xCppDir,
                    workspace, frameCount + "", blockSize + "", tolerance + "", stepSize + "", type ,count + "");
        }

        System.out.println("type: " + type + " count: " + count);

        return dandere2xPB;
    }

    /*
    This is only for linux functions. This function will create a waifu2x_script.sh which the user is to
    run during runtime.
     */
    private void createWaifu2xScript() {

        log.println("creating script...");
        File script = new File(workspace + separator + "waifu2x_script.sh");
        if (script.exists()) {
            script.delete();
        }

        Set<PosixFilePermission> ownerWritable = PosixFilePermissions.fromString("rwxrwxr-x");
        FileAttribute<?> permissions = PosixFilePermissions.asFileAttribute(ownerWritable);
        try {
            Files.createFile(script.toPath(), permissions);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedWriter writer1 = null;
        try {
            writer1 = new BufferedWriter(new FileWriter(script));
        } catch (IOException e) {
            e.printStackTrace();
        }

        StringBuilder commands = new StringBuilder();
        commands.append("x-terminal-emulator\n");
        commands.append("th " + dandereDir + " -m noise_scale -noise_level 3 -i " + fileLocation + "frame1.jpg" +
                " -o " + mergedDir + "merged_1.jpg\n");

        commands.append("th " + dandereDir + " -m noise_scale -noise_level 3 -resume 1 -l "
                + workspace + "frames.txt -o " + upscaledLocation + "output_%d.png");

        try {

            writer1.write(commands.toString());
            writer1.close();

        } catch (IOException e) {
            System.out.println("could not create script!");
            log.println("could not create script!");
        }
    }


    /*
    Create text files for waifu2x to upscale, and create commands for user to input when
    they want to merge the completed files back into an mp4.
     */
    private void initialSetup() {
        StringBuilder frames = new StringBuilder();
        StringBuilder commands = new StringBuilder();

        commands.append("Run these commands after runtime to remerge the videos at your own leisure.\n");
        commands.append("ffmpeg -f image2 -framerate " + this.frameRate + " -i " + mergedDir + "merged_%d.jpg -r 24 " + workspace + "nosound.mp4\n");

        commands.append("ffmpeg -i " + workspace + "nosound.mp4" + " -i " + workspace + "audio.mp3 -c copy "
                + workspace + "sound.mp4\n");


        //this inner if statement creates a list of files for waifu2x to upscale. Waifu2x needs a list to upscale
        //for bulk upscaling.
        for (int x = 1; x < frameCount; x++)
            frames.append(outLocation + "output_" + DandereUtils.getLexiconValue(6, x) + ".jpg" + "\n");


        try {
            BufferedWriter writer1 = new BufferedWriter(new FileWriter(workspace + separator + "frames" + ".txt"));
            BufferedWriter writer2 = new BufferedWriter(new FileWriter(workspace + separator + "commands" + ".txt"));
            writer1.write(frames.toString());
            writer1.close();
            writer2.write(commands.toString());
            writer2.close();
        } catch (IOException e) {
            log.println("could not write commands or frames correctly");
        }
    }

}
