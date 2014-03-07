package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

public class ApplySrg2Source extends DefaultTask
{
    @InputFile
    private DelayedFile srg;
    
    @InputFiles
    private FileCollection libs;
    private DelayedFile projectFile; // to get classpath from a subproject
    private String projectConfig; // Also for a subProject
    
    private DelayedFile in;
    private DelayedFile out;
    
    @TaskAction
    public void doTask() throws IOException
    {
        File in = getIn();
        File out = this.out == null ? in : getOut();
        File rangemap = File.createTempFile("rangemap", "tmp", this.getTemporaryDir());
        File srg = getSrg();
        
        InputSupplier inSup;
        OutputSupplier outSup;
        
        boolean isJar = in.getPath().endsWith(".jar") || in.getPath().endsWith(".zip");
        
        if (isJar)
        {
            // setup input
            inSup = new ZipInputSupplier();
            ((ZipInputSupplier) inSup).readZip(in);
            
            // setup output
            outSup = new ZipOutputSupplier(out);
        }
        else // folder!
        {
            @SuppressWarnings("resource")
            FolderSupplier fSup = new FolderSupplier(in);
            
            inSup = fSup;
            if (in == out)
                outSup = fSup;
            else
                outSup = new FolderSupplier(out);
        }
        
        generateRangeMap(inSup, rangemap);
        applyRangeMap(inSup, outSup, srg, rangemap);
        
        
        inSup.close();
        outSup.close();
    }
    
    private void generateRangeMap(InputSupplier inSup, File rangeMap)
    {
        RangeExtractor extractor = new RangeExtractor();
        extractor.addLibs(getLibs().getAsPath()).setSrc(inSup);
        
        PrintStream stream = new PrintStream(Constants.createLogger(getLogger(), LogLevel.DEBUG));
        extractor.setOutLogger(stream);
        
        boolean worked = extractor.generateRangeMap(rangeMap);
        
        stream.close();
        
        if (!worked)
            throw new RuntimeException("RangeMap generation Failed!!!");
    }
    
    private void applyRangeMap(InputSupplier inSup, OutputSupplier outSup, File srg, File rangeMap) throws IOException
    {
        RangeApplier app = new RangeApplier().readSrg(srg);
        
        PrintStream stream = new PrintStream(Constants.createLogger(getLogger(), LogLevel.DEBUG));
        app.setOutLogger(stream);
        
        app.remapSources(inSup, outSup, rangeMap, false);
    }

    @InputFiles
    public FileCollection getIns()
    {
        File inFile = getIn();
        if (inFile.isDirectory())
            return getProject().fileTree(inFile);
        else
            return getProject().files(inFile);
    }
    
    public File getIn()
    {
        return in.call();
    }

    public void setIn(DelayedFile in)
    {
        this.in = in;
    }
    
    @OutputFiles
    public FileCollection getOuts()
    {
        File outFile = getOut();
        if (outFile.isDirectory())
            return getProject().fileTree(outFile);
        else
            return getProject().files(outFile);
    }

    public File getOut()
    {
        if (out == null)
            return getIn();
        else
            return out.call();
    }

    public void setOut(DelayedFile out)
    {
        this.out = out;
    }

    public FileCollection getLibs()
    {
        if (projectFile != null && libs == null) // libs == null to avoid doing this any more than necessary..
        {
            Project proj = BasePlugin.getProject(projectFile.call(), getProject());
            libs = proj.getConfigurations().getByName(projectConfig);
        }
        
        return libs;
    }

    public void setLibs(FileCollection libs)
    {
        this.libs = libs;
    }
    
    public void setLibsFromProject(DelayedFile buildscript, String config)
    {
        this.projectFile = buildscript;
        this.projectConfig = config;
    }
    
    public File getSrg()
    {
        return srg.call();
    }

    public void setSrg(DelayedFile srg)
    {
        this.srg = srg;
    }
}