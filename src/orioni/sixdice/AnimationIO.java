package orioni.sixdice;

import orioni.jz.awt.image.ImageUtilities;
import orioni.jz.io.files.FileRegularExpressionFilter;
import orioni.jz.io.files.FileUtilities;
import orioni.jz.math.MathUtilities;
import orioni.jz.util.Pair;
import orioni.jz.util.configuration.Configuration;
import orioni.jz.util.configuration.ConfigurationElement;
import orioni.jz.util.configuration.IntegerConfigurationElement;
import orioni.jz.util.strings.StringUtilities;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * This utilities class is designed to provide functionality for loading and saving image files using {@link Animation}
 * objects.  The general assumption is that each image in the {@link Animation} will be saved as a different image file
 * with the filename <code>filename__xx__yy.ext</code> where: <ul> <li><code>filename</code> is the name of the
 * animation file,</li> <li><code>xx</code> is the direction number,</li> <li><code>yy</code> is the frame number,</li>
 * <li><code>ext</code> is the file extension (such as ".jpg" or ".png"), and</li> <li>"<code>__</code>" is a specified
 * separation string.</li> </ul> Obviously, the same filename and separation character must be specified when loading an
 * image series which has been saved in this form.
 *
 * @author Zachary Palmer
 */
public class AnimationIO
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

    /**
     * The {@link ConfigurationElement} which describes the X offset base in an exported series metafile.
     */
    public static final IntegerConfigurationElement META_ELEMENT_X_OFFSET =
            new IntegerConfigurationElement("offset.x", 0);
    /**
     * The {@link ConfigurationElement} which describes the Y offset base in an exported series metafile.
     */
    public static final IntegerConfigurationElement META_ELEMENT_Y_OFFSET =
            new IntegerConfigurationElement("offset.y", 0);

    /**
     * This {@link String} contains the suffix, period included, of the metafile used by the import/export system.
     */
    public static final String METAFILE_SUFFIX = ".meta";

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * Private constructor.
     */
    private AnimationIO()
    {
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Creates a {@link Set} of {@link File}s which are used by the image series to which the specified file belongs.
     *
     * @param file      A {@link File} which is part of an image series.
     * @param separator The separator string used to separate information in the filename of image series files.
     * @return A {@link Set} containing all such files used in the animation.  If the file is not of proper format to be
     *         used in an animation, an empty set is returned.
     */
    public static Set<File> getImageSeriesParticipants(File file, String separator)
    {
        String[] s = file.getName().split(separator);
        if (s.length != 3) return new HashSet<File>();
        return new HashSet<File>(
                Arrays.asList(
                        file.getParentFile().listFiles(
                                new FileRegularExpressionFilter(s[0] + separator + "\\d+" + separator + "\\d+\\..*"))));
    }

    /**
     * Retrieves the {@link File} which is used to generate the names in the image series in which the provided {@link
     * File} is a participant.  For example, assuming "<code>__</code>" as a separator string, if the provided file is
     * "<code>myAnim__01__05.png</code>", the returned file is "<code>myAnim.png</code>".
     *
     * @param file      The image series file.
     * @param separator The separator string used to separate information in the filename of image series files.
     * @return The file used to generate the series names, or <code>null</code> if the provided file isn't part of an
     *         image series.
     */
    public static File getSeriesGenerationFile(File file, String separator)
    {
        String[] s = FileUtilities.removeFileExtension(file).getName().split(separator);
        if (s.length != 3) return null;
        if ((!MathUtilities.isNumber(s[1], 10)) || (!MathUtilities.isNumber(s[2], 10))) return null;
        return new File(file.getParent() + File.separatorChar + s[0] + '.' + FileUtilities.getFileExtension(file));
    }

    /**
     * Loads an image series and produces an {@link Animation} from it.
     *
     * @param file      The {@link File} to use to generate names for the image files.  This file should not contain
     *                  instances of the separator string.
     * @param separator The separator string to use when generating image file names.
     * @param error     A {@link PrintStream} which should be used to record errors regarding the loading process.
     * @return The generated {@link Animation}, or <code>null</code> if the {@link Animation} could not be loaded.
     */
    public static Animation load(File file, String separator, PrintStream error)
    {
        try
        {
            ArrayList<String> warnings = new ArrayList<String>();

            int periodIndex = file.getName().lastIndexOf('.');
            String name;
            String extension;
            if (periodIndex == -1)
            {
                name = file.getName();
                extension = "";
            } else
            {
                name = file.getName().substring(0, periodIndex);
                extension = file.getName().substring(periodIndex);
            }
            if (!FileUtilities.FILESYSTEM_CASE_SENSITIVE)
            {
                name = name.toLowerCase();
                extension = extension.toLowerCase();
            }
            // this map will contain a mapping between direction/pair coordinates and the contents of the files
            Map<Pair<Integer, Integer>, BufferedImage> images = new HashMap<Pair<Integer, Integer>, BufferedImage>();
            int maxDirection = 0;
            int maxFrame = 0;
            for (File f : file.getParentFile().listFiles())
            {
                String filename = f.getName();
                if (!FileUtilities.FILESYSTEM_CASE_SENSITIVE)
                {
                    filename = filename.toLowerCase();
                }
                if ((filename.startsWith(name)) && (filename.endsWith(extension)))
                {
                    filename = filename.substring(name.length(), filename.length() - extension.length());
                    String[] strings = filename.split(separator);
                    if ((strings.length == 3) && ("".equals(strings[0])))
                    {
                        // looks like a good candidate
                        int direction;
                        int frame;
                        try
                        {
                            direction = Integer.parseInt(strings[1]);
                            frame = Integer.parseInt(strings[2]);
                        } catch (NumberFormatException e)
                        {
                            // obviously not a good candidate ;)
                            warnings.add("Possibly incorrectly named file (could not parse direction or frame): " + f);
                            continue;
                        }
                        if ((direction < 0) || (frame < 0))
                        {
                            // also not a good candidate
                            warnings.add("Possible incorrectly named file (direction<0 or frame<0): " + f);
                            continue;
                        }

                        // ok, this looks usable... can we read it?
                        BufferedImage image = ImageIO.read(f);
                        if (image != null)
                        {
                            images.put(new Pair<Integer, Integer>(direction, frame), image);
                            maxDirection = Math.max(maxDirection, direction);
                            maxFrame = Math.max(maxFrame, frame);
                        } else
                        {
                            warnings.add("Could not read file (no supported reader): " + f);
                        }
                    }
                }
            }

            // If a metadata file exists, read it now.
            int xOffsetBase = 0;
            int yOffsetBase = 0;
            File metafile = new File(file.getParent() + File.separatorChar + name + METAFILE_SUFFIX);
            if ((metafile.exists()) && (metafile.isFile()))
            {
                Configuration conf = new Configuration(META_ELEMENT_X_OFFSET, META_ELEMENT_Y_OFFSET);
                String errorString = conf.load(metafile);
                if (errorString == null)
                {
                    xOffsetBase = conf.getValue(META_ELEMENT_X_OFFSET);
                    yOffsetBase = conf.getValue(META_ELEMENT_Y_OFFSET);
                } else
                {
                    error.println("Could not load metadata file " + metafile + ": " + errorString);
                }
            }

            // read all possible images... now construct the animation
            ArrayList<AnimationFrame> alaf = new ArrayList<AnimationFrame>();
            for (int d = 0; d <= maxDirection; d++)
            {
                for (int f = 0; f <= maxFrame; f++)
                {
                    alaf.add(
                            new AnimationFrame(
                                    images.get(new Pair<Integer, Integer>(d, f)), xOffsetBase, yOffsetBase));
                }
            }

            return new Animation(alaf, maxDirection + 1, maxFrame + 1, warnings);
        } catch (IOException e)
        {
            error.println("<html>An I/O error occurred during import:<br>    " + e + "<br>The import failed.");
            return null;
        }
    }

    /**
     * Saves an {@link Animation} as an image series.
     *
     * @param file       The {@link File} to use to generate names for the image files.
     * @param separator  The separator string to use when generating image file names.
     * @param formatName The informal format name to pass to {@link ImageIO} to write the image.
     * @param animation  The {@link Animation} to write.
     * @param error      A {@link PrintStream} which should be used to record errors regarding the saving process.
     */
    public static void save(File file, String separator, String formatName, Animation animation, PrintStream error)
    {
        int digits = (int) (Math.ceil(Math.log10(Math.max(animation.getDirectionCount(), animation.getFrameCount()))));

        String name = file.getName();
        String withoutExtension;
        String extension;
        String parentPath = file.getParent() + File.separatorChar;
        if (name.lastIndexOf('.') != -1)
        {
            withoutExtension = name.substring(0, name.lastIndexOf('.'));
            extension = name.substring(name.lastIndexOf('.'));
        } else
        {
            withoutExtension = name;
            extension = "";
        }

        for (int d = 0; d < animation.getDirectionCount(); d++)
        {
            for (int f = 0; f < animation.getFrameCount(); f++)
            {
                try
                {
                    if (!ImageUtilities.writeImage(
                            animation.getPaddedImage(d, f), formatName,
                            new File(
                                    parentPath + withoutExtension + separator +
                                    StringUtilities.padLeft(String.valueOf(d), '0', digits) +
                                    separator +
                                    StringUtilities.padLeft(String.valueOf(f), '0', digits) +
                                    extension)))
                    {
                        error.println(
                                "Direction " + d + ", Frame " + f +
                                ": Image writing failed: Image format not supported.");
                    }
                } catch (IOException e)
                {
                    error.println("Direction " + d + ", Frame " + f + ": Image writing failed: " + e.getMessage());
                }
            }
        }
        Configuration conf = new Configuration(META_ELEMENT_X_OFFSET, META_ELEMENT_Y_OFFSET);
        conf.setValue(META_ELEMENT_X_OFFSET, animation.getFirstXIndex());
        conf.setValue(META_ELEMENT_Y_OFFSET, animation.getFirstYIndex());
        File metafile = new File(parentPath + withoutExtension + METAFILE_SUFFIX);
        String errorString = conf.save(metafile);
        if (errorString != null)
        {
            error.println("Error writing metadata file " + metafile + ": " + errorString);
        }
    }

// STATIC METHODS ////////////////////////////////////////////////////////////////

}

// END OF FILE