package orioni.sixdice;

import orioni.jz.awt.AWTUtilities;
import orioni.jz.awt.image.ImageUtilities;
import orioni.jz.awt.image.RestrictableIndexColorModel;
import orioni.jz.common.exception.ParseException;
import orioni.jz.io.files.FileUtilities;
import orioni.jz.util.Pair;
import orioni.jz.util.ProgressTracker;
import orioni.jz.util.ConsoleProgressListener;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * This class is designed to represent all of the core functionality of SixDice: that is, all the functionality which
 * does not depend on a specific user interface.  It maintains an {@link Animation} in memory and provides basic
 * functionality: loading, saving, importing, exporting, manipulation, and batch conversion.
 *
 * @author Zachary Palmer
 */
public class SixDiceCore
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

    /**
     * The currently-loaded {@link Animation}, or <code>null</code> for none.
     */
    protected Animation animation;
    /**
     * The registered {@link AnimationCodec}s in this core.
     */
    protected List<AnimationCodec> codecs;

    /**
     * The {@link Color} for virtual transparency.
     */
    protected Color virtualTransparent;
    /**
     * Whether or not transparent colors are replaced with the virtual color on saves.
     */
    protected boolean transparentToVirtalOnSave;

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * General constructor.
     *
     * @param codecs The {@link AnimationCodec}s for this {@link SixDiceCore} to use.
     */
    public SixDiceCore(AnimationCodec... codecs)
    {
        super();
        animation = null;
        this.codecs = new ArrayList<AnimationCodec>();
        for (AnimationCodec c : codecs) this.codecs.add(c);
        virtualTransparent = null;
        transparentToVirtalOnSave = false;
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Registers a list of codecs with this {@link SixDiceCore}.
     *
     * @param codecs The {@link AnimationCodec}s to register.
     */
    public void addCodecs(AnimationCodec... codecs)
    {
        for (AnimationCodec c : codecs) this.codecs.add(c);
    }

    /**
     * Sets the virtual transparent color for this core.  Pixels of the virtual transparent color (if
     * non-<code>null</code>) are replaced with actual transparent pixels when images are loaded.
     *
     * @param color The new virtual transparent color.
     */
    public void setVirtualTransparentColor(Color color)
    {
        virtualTransparent = color;
    }

    /**
     * Sets whether or not transparent colors are replaced with the virtual color (if non-<code>null</code>) on saving
     * images.
     *
     * @param transparentToVirtualOnSave <code>true</code> if replacement occurs; <code>false</code> otherwise.
     */
    public void setTransparentToVirtualOnSave(boolean transparentToVirtualOnSave)
    {
        transparentToVirtalOnSave = transparentToVirtualOnSave;
    }

    /**
     * Retrieves The {@link Animation} currently loaded in this core.
     *
     * @return The {@link Animation} in memory, or <code>null</code> if no animation is loaded.
     */
    public Animation getAnimation()
    {
        return animation;
    }

    /**
     * Clears the current {@link Animation} in memory.
     */
    public void clearAnimation()
    {
        animation = null;
    }

    /**
     * Creates a new {@link Animation} in memory with a single, blank 1x1 frame.
     */
    public void createNewAnimation()
    {
        animation = new Animation();
    }

    /**
     * Loads an {@link Animation} from the specified {@link File}.
     *
     * @param file    The {@link File} from which to load the {@link Animation}.
     * @param palette The {@link RestrictableIndexColorModel} which should be used as a palette for loading the {@link
     *                Animation}.
     * @param tracker The {@link ProgressTracker} which tracks this operation, or <code>null</code> if no tracker is
     *                desired.
     * @return <code>true</code> if the load was successful; <code>false</code> if it was not.
     * @throws IOException If an I/O error occurs while trying to read the {@link Animation}.
     */
    public boolean loadAnimation(File file, RestrictableIndexColorModel palette, ProgressTracker tracker)
            throws IOException
    {
        animation = null;
        for (AnimationCodec codec : codecs)
        {
            if (codec.getFileType().usesExtension(FileUtilities.getFileExtension(file)))
            {
                try
                {
                    animation = codec.read(file, codec.formatContainsPalette() ? null : palette, tracker);
                    break;
                } catch (ParseException e)
                {
                }
            }
        }
        return (animation != null);
    }

    /**
     * Checks the provided {@link Animation} to ensure that it can be saved in the provided file.  The extension of that
     * file is used to determine which {@link AnimationCodec} to use.
     *
     * @param file The {@link File} into which the {@link Animation} will be saved.
     * @return A {@link List}<code>&lt{@link Pair}&lt;{@link String}, {@link AnimationCodec.MessageType}&gt;&gt;</code>
     *         containing any messages from the codec regarding potential problems saving the {@link Animation}.
     */
    public List<Pair<String, AnimationCodec.MessageType>> checkAnimation(File file)
    {
        if (animation == null)
        {
            return Collections.singletonList(
                    new Pair<String, AnimationCodec.MessageType>(
                            "No animation currently in memory.", AnimationCodec.MessageType.FATAL));
        }

        AnimationCodec codec = codecs.get(0);
        for (AnimationCodec c : codecs)
        {
            if (c.getFileType().usesExtension(FileUtilities.getFileExtension(file)))
            {
                codec = c;
                break;
            }
        }

        return codec.check(animation);
    }

    /**
     * Saves the {@link Animation} in memory to the provided {@link File}.  The extension of that file is used to
     * determine which {@link AnimationCodec} to use.
     *
     * @param file    The {@link File} into which the {@link Animation} will be saved.
     * @param palette The {@link RestrictableIndexColorModel} used in saving the {@link Animation}.
     * @param tracker The {@link ProgressTracker} which tracks this operation, or <code>null</code> if no tracker is
     *                desired.
     * @throws IOException If an I/O error occurs while saving the file.
     */
    public void saveAnimation(File file, RestrictableIndexColorModel palette, ProgressTracker tracker)
            throws IOException
    {
        AnimationCodec codec = codecs.get(0);
        for (AnimationCodec c : codecs)
        {
            if (c.getFileType().usesExtension(FileUtilities.getFileExtension(file)))
            {
                codec = c;
                break;
            }
        }

        codec.write(file, animation, codec.formatContainsPalette() ? null : palette, tracker);
    }

    /**
     * Impoorts an image from the specified {@link File} and creates a new {@link Animation} using it.
     *
     * @param file The {@link File} to import.
     * @return An error message, or <code>null</code> if the operation was successful.
     */
    public String importImage(File file)
    {
        BufferedImage image;
        try
        {
            image = postProcessLoadedImage(ImageIO.read(file));
            if (image == null)
            {
                return "The provided file is not an image or this JRE has no image converter for that format.";
            }
        } catch (IOException e)
        {
            return "The following error occurred when trying to load " + file + ":\n" + e.getMessage();
        }
        animation = new Animation(image);
        return null;
    }

    /**
     * Imports an image into the specified location in the {@link Animation}.
     *
     * @param file      The image {@link File} to import.
     * @param direction The direction in the {@link Animation} for the image to be placed.
     * @param frame     The direction in the {@link Animation} for the image to be placed.
     * @param insert    <code>true</code> if the newly imported image should be inserted; <code>false</code> if it
     *                  should replace its predecessor.
     * @return An error message, or <code>null</code> if the operation was successful.
     */
    public String importImage(File file, int direction, int frame, boolean insert)
    {
        BufferedImage image;
        try
        {
            image = postProcessLoadedImage(ImageIO.read(file));
            if (image == null)
            {
                return "The provided file is not an image or this JRE has no image converter for that format.";
            }
        } catch (IOException e)
        {
            return "The following error occurred when trying to load " + file + ":\n" + e.getMessage();
        }
        if (insert)
        {
            animation.addFrame(frame);
        }
        animation.getFrame(direction, frame).setImage(image);
        return null;
    }

    /**
     * Imports a series of images as an {@link Animation}.
     *
     * @param file      The generator file for the image series.
     * @param separator The separator used to seperate information in the filenames.
     * @return An error message, or <code>null</code> if the operation was successful.
     */
    public String importSeries(File file, String separator)
    {
        animation = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        animation = AnimationIO.load(file, separator, ps);
        ps.close();
        if (baos.size() > 0)
        {
            return baos.toString();
        } else
        {
            for (AnimationFrame frame : animation.getFrames())
            {
                frame.setImage(postProcessLoadedImage(frame.getImage()));
            }
            return null;
        }
    }

    /**
     * Exports an image from the current {@link Animation}.
     *
     * @param file      The file into which the image should be written.
     * @param format    The informal format name to provide to {@link ImageIO}.
     * @param direction The direction of the image to export.
     * @param frame     The frame of the image to export.
     * @return An error message, or <code>null</code> if the export was successful.
     */
    public String exportImage(File file, String format, int direction, int frame)
    {
        try
        {
            BufferedImage image = preProcessSavingImage(
                    ImageUtilities.copyImage(animation.getFrame(direction, frame).getImage()));
            if (!ImageUtilities.writeImage(image, format, file))
            {
                return "The image format " + format + " is not supported.";
            }
        } catch (IOException e)
        {
            return "The following error occurred while trying to export the image:\n" + e.getMessage();
        }
        return null;
    }

    /**
     * Exports the current {@link Animation} as an image series.
     *
     * @param file      The generator file used to produce the names of the image files.
     * @param format    The informal format name to pass to {@link ImageIO}.
     * @param separator The separator string used to separate information in the file names.
     * @return An error message, or <code>null</code> if the operation was successful.
     */
    public String exportSeries(File file, String format, String separator)
    {
        // Create a duplicate of our animation so we can pre-process the images without changing the in-use copy.
        Animation duplicate = new Animation(null, animation.getDirectionCount(), animation.getFrameCount());
        for (int d = 0; d < animation.getDirectionCount(); d++)
        {
            for (int f = 0; f < animation.getFrameCount(); f++)
            {
                duplicate.setFrame(
                        d, f, new AnimationFrame(
                        preProcessSavingImage(animation.getPaddedImage(d, f)),
                        animation.getFirstXIndex(),
                        animation.getFirstYIndex()));
            }
        }

        // Now perform the export.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);

        // TODO: parameterize how the export chooses the format of the numbers for the export files
        AnimationIO.save(file, separator, format, duplicate, ps);
        ps.close();

        if (baos.size() > 0)
        {
            return baos.toString();
        } else
        {
            return null;
        }
    }

    /**
     * This method splits the specified frame into multiple subframes, each of which has a maximum of the specified
     * dimensions.  The new subframes either replace the old frames (if <code>insert</code> is <code>false</code>) or
     * insert new frames into the {@link Animation} (if <code>insert</code> is <code>true</code>).
     *
     * @param direction The direction of the frame to split.
     * @param frame     The frame index of the frame to split.
     * @param width     The maximum width of each subframe.
     * @param height    The maximum height of each subframe.
     * @param insert    <code>true</code> to insert new subframes; <code>false</code> to replace old frames.
     */
    public void splitFrame(int direction, int frame, int width, int height, boolean insert)
    {
        if (animation.getDirectionCount() <= direction) animation.addDirection(direction);
        int currentFrame = frame;
        if (animation.getFrameCount() <= currentFrame) animation.addFrame(currentFrame);

        BufferedImage image = animation.getFrame(direction, currentFrame).getImage();
        int heightInFrames = (int) (Math.ceil(image.getHeight() / (double) height));
        int widthInFrames = (int) (Math.ceil(image.getWidth() / (double) width));

        for (int y = 0; y < heightInFrames; y++)
        {
            for (int x = 0; x < widthInFrames; x++)
            {
                BufferedImage subimage = ImageUtilities.copyImage(
                        image.getSubimage(
                                x * width,
                                y * height,
                                Math.min(
                                        width,
                                        image.getWidth() -
                                        x * width),
                                Math.min(
                                        height,
                                        image.getHeight() -
                                        y * height)));
                if (((insert && (y + x > 0))) || // makes the insert mode work properly
                    (animation.getFrameCount() <= currentFrame))     // makes the replace mode work properly
                {
                    animation.addFrame(currentFrame);
                }
                animation.setFrame(direction, currentFrame, new AnimationFrame(subimage, 0, 0));
                currentFrame++;
            }
        }
    }

    /**
     * Centers the offsets of the frames in the current direction on the center of the specified frame.
     *
     * @param direction The direction over which to center the offsets.
     * @param frame     The frame over which to center the offsets.
     */
    public void centerOffsets(int direction, int frame)
    {
        AnimationFrame f = animation.getFrame(direction, frame);
        animation.adjustOffsets(
                f.getXOffset() + f.getImage().getWidth() / 2, f.getYOffset() - f.getImage().getHeight() / 2);
    }

    /**
     * Post-processes the specified {@link BufferedImage} after import to allow for manipulation such as virtual clear
     * color.
     *
     * @param image The image to process.  This image may be changed.
     * @return The image to use in its stead.
     */
    public BufferedImage postProcessLoadedImage(BufferedImage image)
    {
        if (virtualTransparent != null)
        {
            image = ImageUtilities.copyImage(image);
            ImageUtilities.replaceAll(image, virtualTransparent, AWTUtilities.COLOR_TRANSPARENT);
        }
        return image;
    }

    /**
     * Pre-processes the specified {@link BufferedImage} before export, allowing for manipulation such as virtual clear
     * color replacement.
     *
     * @param image The image to process.  This image may be changed.
     * @return The image to use in its stead.
     */
    public BufferedImage preProcessSavingImage(BufferedImage image)
    {
        if ((virtualTransparent != null) && (transparentToVirtalOnSave))
        {
            ImageUtilities.replaceAll(image, AWTUtilities.COLOR_TRANSPARENT, virtualTransparent);
        }
        return image;
    }

    /**
     * Converts the specified {@link Animation} file using this {@link SixDiceCore}'s memory space and codec set.
     *
     * @param file      The {@link File} to convert.
     * @param format    The image format to use when saving images.
     * @param separator The separator string for the filenames.
     * @param palette   The palette in which the {@link Animation} is stored.
     * @param psOut    The output stream for this process.
     * @param psErr    The error stream for this process.
     */
    public void convertAnimationToImage(File file, String format, String separator, RestrictableIndexColorModel palette,
                                        PrintStream psOut, PrintStream psErr)
    {
        try
        {
            psOut.print("Loading " + file + "... ");
            if (loadAnimation(file, palette, null))
            {
                psOut.println("Complete.");
                file = FileUtilities.replaceFileExtension(file, '.' + format);
                String error;
                psOut.print("Exporting " + file + "... ");
                if (animation.getFrameCount() * animation.getDirectionCount() == 1)
                {
                    error = exportImage(file, format, 0, 0);
                } else
                {
                    error = exportSeries(file, format, separator);
                }
                if (error != null)
                {
                    psErr.println("Error during export: " + error);
                    psOut.println(file + " could not be exported.");
                } else
                {
                    psOut.println("Complete.");
                }
            }
        } catch (IOException e)
        {
            psErr.println("I/O error occurred: " + e.getMessage());
        }
    }

    /**
     * Converts the provided image file into an {@link Animation} using this core's memory space.  If the specified
     * image file is part of an image series, the series is converted instead.
     *
     * @param file         The {@link File} to convert.
     * @param targetCodec The {@link AnimationCodec} with which to write the {@link Animation} file.
     * @param separator    The separator string to use to identify parts of an image set.
     * @param palette      The palette in which to write the {@link Animation}.
     * @param psOut       The output stream for this process.
     * @param psErr       The error stream for this process.
     * @return The set of files used by this operation.
     */
    public Set<File> convertImageToAnimation(File file, AnimationCodec targetCodec, String separator,
                                             RestrictableIndexColorModel palette, PrintStream psOut,
                                             PrintStream psErr)
    {
        Set<File> ret = new HashSet<File>();
        try
        {
            File generator = AnimationIO.getSeriesGenerationFile(file, separator);
            String error;
            if (generator == null)
            {
                psOut.print("Importing " + file + "... ");
                error = importImage(file);
                if (error == null) psOut.println("Complete.");
            } else
            {
                ret = AnimationIO.getImageSeriesParticipants(file, separator);
                psOut.print("Importing " + generator + "... ");
                error = importSeries(generator, separator);
                if (error == null) psOut.println("Complete.");
                file = generator;
            }
            if (error == null)
            {
                file = FileUtilities.replaceFileExtension(file, '.' + targetCodec.getFileType().getExtensions()[0]);
                psOut.print("Saving " + file + "... ");

                ProgressTracker pt = new ProgressTracker();
                pt.addListener(new ConsoleProgressListener(psOut));

                saveAnimation(file, palette, pt);
                psOut.println("Complete.");
            } else
            {
                psErr.println("Error while importing: " + error);
            }
        } catch (IOException e)
        {
            psErr.println("I/O error occurred: " + e.getMessage());
        }
        return ret;
    }

    /**
     * This method is designed to batch convert animations into other animations.
     *
     * @param directory    The directory in which to perform the batch conversion.
     * @param recursive    <code>true</code> to perform the operation recursively; <code>false</code> otherwise.
     * @param sourceCodec The {@link AnimationCodec} used to read the animations.
     * @param targetCodec The {@link AnimationCodec} used to write the animations.
     * @param palette      The palette in which the animations are rendered.
     * @param out          The standard output stream for this operation.
     * @param err          The error stream for this operation.
     * @param lowestLevel A <code>boolean</code> indicating whether or not this is the lowest-level call to the batch
     *                     conversion method.  If so, starting and ending messages will be displayed.  Otherwise, they
     *                     will not.  This is necessary as the batch conversion method handles directory recursion by
     *                     recursively calling itself.
     */
    public void batchConvert(File directory, boolean recursive, AnimationCodec sourceCodec,
                             AnimationCodec targetCodec, RestrictableIndexColorModel palette,
                             OutputStream out, OutputStream err, boolean lowestLevel)
    {
        PrintStream psOut;
        PrintStream psErr;
        if (out instanceof PrintStream)
        {
            psOut = (PrintStream) (out);
        } else
        {
            psOut = new PrintStream(out);
        }
        if (err instanceof PrintStream)
        {
            psErr = (PrintStream) (err);
        } else
        {
            psErr = new PrintStream(err);
        }

        int priority = Thread.currentThread().getPriority();
        if (lowestLevel)
        {
            psOut.println("Starting batch conversion...");
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        }

        for (File file : directory.listFiles())
        {
            if (file.isDirectory())
            {
                if (recursive)
                {
                    batchConvert(file, recursive, sourceCodec, targetCodec, palette, psOut, psErr, false);
                }
            } else
            {
                if (sourceCodec.getFileType().usesExtension(FileUtilities.getFileExtension(file)))
                {
                    try
                    {
                        psOut.print("Loading " + file + "... ");
                        if (loadAnimation(file, palette, null))
                        {
                            psOut.println("Complete.");
                            file = FileUtilities.replaceFileExtension(
                                    file, targetCodec.getFileType().getExtensions()[0]);
                            psOut.print("Saving " + file + "... ");

                            ProgressTracker pt = new ProgressTracker();
                            pt.addListener(new ConsoleProgressListener(psOut));

                            saveAnimation(file, palette, pt);
                            psOut.println("Complete.");
                        }
                    } catch (IOException e)
                    {
                        psErr.println("I/O error occurred: " + e.getMessage());
                    }
                }
            }
        }

        if (lowestLevel)
        {
            Thread.currentThread().setPriority(priority);
            psOut.println("Batch conversion complete.");
            psOut.close();
            psErr.close();
        }
    }

    /**
     * This method is designed to batch convert animations into image series.
     *
     * @param directory    The directory in which to perform the batch conversion.
     * @param recursive    <code>true</code> to perform the operation recursively; <code>false</code> otherwise.
     * @param sourceCodec The {@link AnimationCodec} used to read the animations.
     * @param format       The format in which to write the images.
     * @param separator    The separator characters used to generate filenames.
     * @param palette      The palette in which to read the animations.
     * @param out          The standard output stream for this operation.
     * @param err          The error stream for this operation.
     * @param lowestLevel A <code>boolean</code> indicating whether or not this is the lowest-level call to the batch
     *                     conversion method.  If so, starting and ending messages will be displayed.  Otherwise, they
     *                     will not.  This is necessary as the batch conversion method handles directory recursion by
     *                     recursively calling itself.
     */
    public void batchConvert(File directory, boolean recursive, AnimationCodec sourceCodec,
                             String format, String separator, RestrictableIndexColorModel palette,
                             OutputStream out, OutputStream err, boolean lowestLevel)
    {
        PrintStream psOut;
        PrintStream psErr;
        if (out instanceof PrintStream)
        {
            psOut = (PrintStream) (out);
        } else
        {
            psOut = new PrintStream(out);
        }
        if (err instanceof PrintStream)
        {
            psErr = (PrintStream) (err);
        } else
        {
            psErr = new PrintStream(err);
        }

        int priority = Thread.currentThread().getPriority();
        if (lowestLevel)
        {
            psOut.println("Starting batch conversion...");
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        }

        for (File file : directory.listFiles())
        {
            if (file.isDirectory())
            {
                if (recursive)
                {
                    batchConvert(file, recursive, sourceCodec, format, separator, palette, psOut, psErr, false);
                }
            } else
            {
                if (sourceCodec.getFileType().usesExtension(FileUtilities.getFileExtension(file)))
                {
                    convertAnimationToImage(file, format, separator, palette, psOut, psErr);
                }
            }
        }

        if (lowestLevel)
        {
            Thread.currentThread().setPriority(priority);
            psOut.println("Batch conversion complete.");
            psOut.close();
            psErr.close();
        }
    }

    /**
     * This method is designed to batch convert image series into animation files.
     *
     * @param directory     The directory in which to perform the batch conversion.
     * @param recursive     <code>true</code> to perform the operation recursively; <code>false</code> otherwise.
     * @param sourceFilter The {@link java.io.FileFilter} used to determine if a file should be used.
     * @param targetCodec  The {@link AnimationCodec} used to write images.
     * @param separator     The separator string used to generate filenames.
     * @param palette       The palette in which the {@link Animation}s will be written.
     * @param out           The standard output stream for this operation.
     * @param err           The error stream for this operation.
     * @param lowestLevel  A <code>boolean</code> indicating whether or not this is the lowest-level call to the batch
     *                      conversion method.  If so, starting and ending messages will be displayed.  Otherwise, they
     *                      will not.  This is necessary as the batch conversion method handles directory recursion by
     *                      recursively calling itself.
     */
    public void batchConvert(File directory, boolean recursive, FileFilter sourceFilter, AnimationCodec targetCodec,
                             String separator, RestrictableIndexColorModel palette, OutputStream out, OutputStream err,
                             boolean lowestLevel)
    {
        PrintStream psOut;
        PrintStream psErr;
        if (out instanceof PrintStream)
        {
            psOut = (PrintStream) (out);
        } else
        {
            psOut = new PrintStream(out);
        }
        if (err instanceof PrintStream)
        {
            psErr = (PrintStream) (err);
        } else
        {
            psErr = new PrintStream(err);
        }

        int priority = Thread.currentThread().getPriority();
        if (lowestLevel)
        {
            psOut.println("Starting batch conversion...");
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        }

        Set<File> usedFiles = new HashSet<File>();
        for (File file : directory.listFiles())
        {
            if (file.isDirectory())
            {
                if (recursive)
                {
                    batchConvert(
                            file, recursive, sourceFilter, targetCodec, separator, palette, psOut, psErr,
                            false);
                }
            } else
            {
                if ((!usedFiles.contains(file)) && (sourceFilter.accept(file)))
                {
                    usedFiles.addAll(convertImageToAnimation(file, targetCodec, separator, palette, psOut, psErr));
                }
            }
        }

        if (lowestLevel)
        {
            Thread.currentThread().setPriority(priority);
            psOut.println("Batch conversion complete.");
            psOut.close();
            psErr.close();
        }
    }

    /**
     * Duplicates this {@link SixDiceCore} in its current state, excepting that the new core does not have a loaded
     * {@link Animation}.  This is used by batch conversion utilities to copy settings but to avoid changing the loaded
     * content of the original core.
     */
    public SixDiceCore copy()
    {
        SixDiceCore ret = new SixDiceCore(codecs.toArray(new AnimationCodec[0]));
        ret.setVirtualTransparentColor(virtualTransparent);
        ret.setTransparentToVirtualOnSave(transparentToVirtalOnSave);
        return ret;
    }

// STATIC METHODS ////////////////////////////////////////////////////////////////

// CONTAINED CLASSES /////////////////////////////////////////////////////////////

}

// END OF FILE