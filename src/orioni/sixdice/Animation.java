package orioni.sixdice;

import orioni.jz.awt.AWTUtilities;
import orioni.jz.awt.image.ImageUtilities;
import orioni.jz.io.files.FileUtilities;
import orioni.jz.util.Pair;
import orioni.jz.util.strings.StringUtilities;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * This class is designed to represent a set of {@link AnimationFrame}s.  An {@link AnimationFrame} contains a single
 * image and a position in a virtual coordinate space.  All {@link AnimationFrame}s in a single {@link Animation} exist
 * in the same coordinate space.  The {@link Animation} itself is assumed to have multiple "directions" (presumably
 * indicating the animation in a series of different directions) and multiple "frames" in each direction (presumably
 * intended to be displayed in sequence).  Each direction has the same number of frames.
 *
 * @author Zachary Palmer
 */
public class Animation
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

    /**
     * The {@link List} of {@link AnimationFrame}s in this {@link Animation}.
     */
    protected List<AnimationFrame> frameList;
    /**
     * The number of directions in this {@link Animation}.
     */
    protected int directions;
    /**
     * The number of frames in this {@link Animation}.
     */
    protected int frames;
    /**
     * The {@link Color} which should be synonymous with transparent, or <code>null</code> if no such color exists.
     */
    protected int transparentIndex;

    /**
     * The warnings for this {@link Animation}.
     */
    protected java.util.List<String> warnings;

    /**
     * The optional data for this {@link Animation}.  The information stored within is dependent upon application.
     */
    protected byte[] optionalData;

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * Skeleton constructor.  Assumes a single-frame animation with a 1x1 image.
     */
    public Animation()
    {
        this(new AnimationFrame());
    }

    /**
     * Skeleton constructor.  Used for single frame animations.
     *
     * @param image The {@link Image} to use as the single frame.
     * @throws IllegalArgumentException If the size of the image list is not equal to the product of the frame count and
     *                                  the direction count.
     */
    public Animation(Image image)
    {
        this(Collections.singletonList(new AnimationFrame(ImageUtilities.bufferImage(image), 0, 0)), 1, 1);
    }

    /**
     * Skeleton constructor.  Used for single frame animations.
     *
     * @param frame The {@link AnimationFrame} to use.
     * @throws IllegalArgumentException If the size of the image list is not equal to the product of the frame count and
     *                                  the direction count.
     */
    public Animation(AnimationFrame frame)
    {
        this(Collections.singletonList(frame), 1, 1);
    }

    /**
     * Skeleton constructor.  Assumes a blank warnings list.
     *
     * @param frameList The {@link List} of {@link AnimationFrame}s to use.  If this value is <code>null</code>, all of
     *                   the specified animations are assumed to be blank.
     * @param directions The number of directions in this {@link Animation}.
     * @param frames     The number of frames in this {@link Animation}.
     * @throws IllegalArgumentException If the size of the image list is not equal to the product of the frame count and
     *                                  the direction count.
     */
    public Animation(List<AnimationFrame> frameList, int directions, int frames)
            throws IllegalArgumentException
    {
        this(frameList, directions, frames, new ArrayList<String>());
    }

    /**
     * General constructor.
     *
     * @param frameList The {@link List} of {@link AnimationFrame}s to use.  If this value is <code>null</code>, all of
     *                   the specified animations are assumed to be blank.
     * @param directions The number of directions in this {@link Animation}.
     * @param frames     The number of frames in this {@link Animation}.
     * @param warnings   A {@link List} of warnings which were issued upon the retrieval of the frame data.
     * @throws IllegalArgumentException If the size of the image list is not equal to the product of the frame count and
     *                                  the direction count.
     */
    public Animation(List<AnimationFrame> frameList, int directions, int frames, List<String> warnings)
            throws IllegalArgumentException
    {
        super();
        if ((frameList != null) && (frameList.size() != directions * frames))
        {
            throw new IllegalArgumentException(
                    "Discrepancy between frame list size (" + frameList.size() + ") and the direction/frame counts (" +
                    directions +
                    " and " +
                    frames +
                    ").  List size must be the product of the other two.");
        }
        this.warnings = warnings;

        initialize(frameList, directions, frames);
    }

    /**
     * Copying constructor.  Creates this {@link Animation} as a deep copy of the one provided.
     *
     * @param animation The animation to copy.
     */
    public Animation(Animation animation)
    {
        super();
        frameList = new ArrayList<AnimationFrame>();
        for (AnimationFrame af : animation.frameList)
        {
            frameList.add(
                    new AnimationFrame(ImageUtilities.copyImage(af.getImage()), af.getXOffset(), af.getYOffset()));
        }
        directions = animation.getDirectionCount();
        frames = animation.getFrameCount();
        if (animation.getOptionalData() == null)
        {
            optionalData = null;
        } else
        {
            optionalData = new byte[animation.getOptionalData().length];
            System.arraycopy(animation.getOptionalData(), 0, optionalData, 0, optionalData.length);
        }
        transparentIndex = animation.transparentIndex;
        warnings = new ArrayList<String>();
        for (String s : animation.getWarnings()) warnings.add(s);
    }

    /**
     * General constructor.  This method creates an animation file out of the images contained in the provided files. If
     * reading any of the files fails, an {@link IOException} is thrown.  The files must be named in the same manner as
     * that in which SixDice saves export series.  Missing files will be replaced with a 1x1 transparent image.
     *
     * @param file      The {@link File} used to generate filenames for the images.
     * @param separator The separator string used to separate the direction and frame numbers from the file's name.
     * @throws IOException If an I/O error occurs.
     */
    public Animation(File file, String separator)
            throws IOException
    {
        warnings = new ArrayList<String>();

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

        // read all possible images... now construct the animation
        ArrayList<AnimationFrame> alaf = new ArrayList<AnimationFrame>();
        for (int d = 0; d <= maxDirection; d++)
        {
            for (int f = 0; f <= maxFrame; f++)
            {
                alaf.add(new AnimationFrame(images.get(new Pair<Integer, Integer>(d, f)), 0, 0));
            }
        }

        // prepared content!
        initialize(alaf, maxDirection + 1, maxFrame + 1);
    }

    /**
     * Initializes this class.
     *
     * @param frameList The {@link List} of {@link AnimationFrame}s to use.  If this value is <code>null</code>, all of
     *                   the specified animations are assumed to be blank.
     * @param directions The number of directions in this {@link Animation}.
     * @param frames     The number of frames in this {@link Animation}.
     */
    private void initialize(List<AnimationFrame> frameList, int directions, int frames)
    {
        if (warnings == null) warnings = new ArrayList<String>();
        if (frameList == null)
        {
            this.frameList = new ArrayList<AnimationFrame>();
            for (int i = 0; i < directions * frames; i++)
            {
                this.frameList.add(new AnimationFrame());
            }
        } else
        {
            this.frameList = new ArrayList<AnimationFrame>(frameList); // in case the provided frame list is a singleton
            for (int i = 0; i < this.frameList.size(); i++)
            {
                if ((this.frameList.get(i) == null) || (this.frameList.get(i).getImage() == null))
                {
                    this.frameList.set(i, new AnimationFrame());
                }
            }
        }
        this.directions = directions;
        this.frames = frames;

        optionalData = new byte[0];
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Retrieves the optional data array.  The returned value is the actual array, not a copy.
     *
     * @return The optional data array.
     */
    public byte[] getOptionalData()
    {
        return optionalData;
    }

    /**
     * Sets the optional data array.
     *
     * @param value The new value for the optional data array.
     */
    public void setOptionalData(byte[] value)
    {
        optionalData = value;
    }

    /**
     * Adds the provided list of warnings to this {@link Animation}'s warnings.
     *
     * @param warnings The warnings to add.
     */
    public void addWarnings(List<String> warnings)
    {
        this.warnings.addAll(warnings);
    }

    /**
     * Retrieves the warnings for this {@link Animation}.  Warnings may indicate that the source of the {@link
     * Animation} object is dubious in some way.
     */
    public String[] getWarnings()
    {
        return warnings.toArray(StringUtilities.EMPTY_STRING_ARRAY);
    }

    /**
     * Performs boundary checking on the provided direction number.
     *
     * @param direction    The direction index to test.
     * @param allowAtEnd If <code>true</code>, the legal range is <code>[0,d]</code>, where <code>d</code> is the
     *                     number of direction in this image.  If <code>false</code>, the legal range is
     *                     <code>[0,d)</code>.  For example, a caller dealing with insertion may provide
     *                     <code>true</code> here, where a caller dealing with retrieval would provide
     *                     <code>false</code>.
     * @throws IndexOutOfBoundsException If the provided direction is not within bounds.
     */
    protected void checkDirectionIndex(int direction, boolean allowAtEnd)
            throws IndexOutOfBoundsException
    {
        if ((direction < 0) || (direction > getDirectionCount()) || ((direction == getDirectionCount()) &&
                                                                     (!allowAtEnd)))
        {
            throw new IndexOutOfBoundsException(
                    direction + " out of bounds [0," + getDirectionCount() +
                    ((allowAtEnd) ? "]" : ")"));
        }
    }

    /**
     * Performs boundary checking on the provided frame number.
     *
     * @param frame        The frame index to test.
     * @param allowAtEnd If <code>true</code>, the legal range is <code>[0,d]</code>, where <code>d</code> is the
     *                     number of frame in this image.  If <code>false</code>, the legal range is <code>[0,d)</code>.
     *                     For example, a caller dealing with insertion may provide <code>true</code> here, where a
     *                     caller dealing with retrieval would provide <code>false</code>.
     * @throws IndexOutOfBoundsException If the provided frame is not within bounds.
     */
    protected void checkFrameIndex(int frame, boolean allowAtEnd)
            throws IndexOutOfBoundsException
    {
        if ((frame < 0) || (frame > getFrameCount()) || ((frame == getFrameCount()) &&
                                                         (!allowAtEnd)))
        {
            throw new IndexOutOfBoundsException(
                    frame + " out of bounds [0," + getFrameCount() +
                    ((allowAtEnd) ? "]" : ")"));
        }
    }

    /**
     * Adds a frame before the specified index.  For example, if this Animation has 2 frames per direction and this
     * method is called with the parameter <code>1</code>, a new frame is added between frames <code>0</code> and
     * <code>1</code>.
     *
     * @param index The index before which a new frame should be added.
     * @throws IndexOutOfBoundsException If the provided index is less than <code>0</code> or greater than the number of
     *                                   frames in this Animation per direction.
     */
    public void addFrame(int index)
            throws IndexOutOfBoundsException
    {
        checkFrameIndex(index, true);
        if (directions == 0)
        {
            frameList.add(new AnimationFrame());
            directions++;
        } else
        {
            for (int i = directions - 1; i >= 0; i--)
            {
                frameList.add(i * frames + index, new AnimationFrame());
            }
        }
        frames++;
    }

    /**
     * Adds a direction before the specified index.
     *
     * @param index The index before which a new direction should be added.
     * @throws IndexOutOfBoundsException If the provided index is less than <code>0</code> or greater than the number of
     *                                   directions in this Animation.
     */
    public void addDirection(int index)
            throws IndexOutOfBoundsException
    {
        checkDirectionIndex(index, true);
        if (frames == 0)
        {
            frameList.add(new AnimationFrame());
            frames++;
        } else
        {
            for (int i = 0; i < frames; i++)
            {
                frameList.add(index * frames, new AnimationFrame());
            }
        }
        directions++;
    }

    /**
     * Retrieves the {@link AnimationFrame} at the specified location.
     *
     * @param direction The direction for the frame.
     * @param frame     The frame index for the frame.
     * @return The {@link AnimationFrame} in question.
     * @throws IndexOutOfBoundsException If either index is less than zero or greater than or equal to its respective
     *                                   limit.
     */
    public AnimationFrame getFrame(int direction, int frame)
            throws IndexOutOfBoundsException
    {
        checkDirectionIndex(direction, false);
        checkFrameIndex(frame, false);
        return frameList.get(direction * frames + frame);
    }

    /**
     * Retrieves a {@link List} which will contain the frames of this {@link Animation} in order.  The first direction's
     * frames are listed first, followed by the second direction's frames, and so on.  The returned frame list is a copy
     * and will not be updated if changes to the {@link Animation} occur, nor vice versa.
     *
     * @return A {@link List} of this {@link Animation}'s frames.
     */
    public List<AnimationFrame> getFrames()
    {
        return new ArrayList<AnimationFrame>(frameList);
    }

    /**
     * Changes the {@link AnimationFrame} at the specified location.
     *
     * @param direction       The direction for the frame.
     * @param frame           The frame index for the frame.
     * @param animationFrame The Animation frame to set.
     * @return The {@link AnimationFrame} in question.
     * @throws IndexOutOfBoundsException If either index is less than zero or greater than or equal to its respective
     *                                   limit.
     */
    public AnimationFrame setFrame(int direction, int frame, AnimationFrame animationFrame)
            throws IndexOutOfBoundsException
    {
        checkDirectionIndex(direction, false);
        checkFrameIndex(frame, false);
        return frameList.set(direction * frames + frame, animationFrame);
    }

    /**
     * Removes a frame (per direction) from this Animation.
     *
     * @param index The index of the frame to remove.
     * @throws IndexOutOfBoundsException If the provided index is less than <code>0</code> or greater than or equal to
     *                                   the number of frames per direction in this Animation.
     */
    public void removeFrame(int index)
            throws IndexOutOfBoundsException
    {
        checkFrameIndex(index, false);
        for (int i = getDirectionCount() - 1; i >= 0; i--)
        {
            frameList.remove(i * getFrameCount() + index);
        }
        frames--;
    }

    /**
     * Removes a direction from this Animation.
     *
     * @param index The index of the direction to remove.
     * @throws IndexOutOfBoundsException If the provided index is less than <code>0</code> or greater than or equal to
     *                                   the number of directions in this Animation.
     */
    public void removeDirection(int index)
            throws IndexOutOfBoundsException
    {
        checkDirectionIndex(index, false);
        for (int i = 0; i < getFrameCount(); i++)
        {
            frameList.remove(index * getFrameCount());
        }
        directions--;
    }

    /**
     * Retrieves the number of frames per direction for this {@link Animation}.
     *
     * @return The number of frames per direction for this {@link Animation}.
     */
    public int getFrameCount()
    {
        return frames;
    }

    /**
     * Retrieves the number of directions in this {@link Animation}.
     *
     * @return The number of directions in this {@link Animation}.
     */
    public int getDirectionCount()
    {
        return directions;
    }

    /**
     * Retrieves the smallest X offset which appears in this {@link Animation}.
     *
     * @return The samllest X offset which appears in this {@link Animation}.
     */
    public int getSmallestXOffset()
    {
        int min = Integer.MAX_VALUE;
        for (AnimationFrame frame : frameList)
        {
            min = Math.min(frame.getXOffset(), min);
        }
        return min;
    }

    /**
     * Retrieves the largest X offset which appears in this {@link Animation}.
     *
     * @return The largest X offset which appears in this {@link Animation}.
     */
    public int getLargestXOffset()
    {
        int max = Integer.MIN_VALUE;
        for (AnimationFrame frame : frameList)
        {
            max = Math.max(frame.getXOffset(), max);
        }
        return max;
    }

    /**
     * Retrieves the smallest Y offset which appears in this {@link Animation}.
     *
     * @return The samllest Y offset which appears in this {@link Animation}.
     */
    public int getSmallestYOffset()
    {
        int min = Integer.MAX_VALUE;
        for (AnimationFrame frame : frameList)
        {
            min = Math.min(frame.getYOffset(), min);
        }
        return min;
    }

    /**
     * Retrieves the largest Y offset which appears in this {@link Animation}.
     *
     * @return The largest Y offset which appears in this {@link Animation}.
     */
    public int getLargestYOffset()
    {
        int max = Integer.MIN_VALUE;
        for (AnimationFrame frame : frameList)
        {
            max = Math.max(frame.getYOffset(), max);
        }
        return max;
    }

    /**
     * Retrieves the first X index in the virtual coordinate space.  This is the X coordinate of the leftmost pixel
     * occupied by the leftmost image.
     *
     * @return The first X index in the virtual coordinate space.
     */
    public int getFirstXIndex()
    {
        return getSmallestXOffset();
    }

    /**
     * Retrieves the first Y index in the virtual coordinate space.  This is the Y coordinate of the topmost pixel
     * occupied by the topmost image.
     *
     * @return The first Y index in the virtual coordinate space.
     */
    public int getFirstYIndex()
    {
        return getSmallestYOffset();
    }

    /**
     * Retrieves the last X index in the virtual coordinate space.  This is the X coordinate of the rightmost pixel
     * occupied by the rightmost image.
     *
     * @return The last X index in the virtual coordinate space.
     */
    public int getLastXIndex()
    {
        int max = Integer.MIN_VALUE;
        for (AnimationFrame frame : frameList)
        {
            max = Math.max(frame.getXOffset() + frame.getImage().getWidth() - 1, max);
        }
        return max;
    }

    /**
     * Retrieves the last Y index in the virtual coordinate space.  This is the Y coordinate of the rightmost piYel
     * occupied by the rightmost image.
     *
     * @return The last Y index in the virtual coordinate space.
     */
    public int getLastYIndex()
    {
        int max = Integer.MIN_VALUE;
        for (AnimationFrame frame : frameList)
        {
            max = Math.max(frame.getYOffset() + frame.getImage().getHeight() - 1, max);
        }
        return max;
    }

    /**
     * Resizes all images in the current animation on the given scale.
     *
     * @param scale          The scale on which to resize the images.  A scale of <code>1.0</code> is an identity
     *                       transform.
     * @param adjustOffsets If <code>true</code>, the offsets of each frame are adjusted by the same amount.
     * @param hints          One of the {@link Image}<code>.SCALE_XXXX</code> values describing how the images should be
     *                       resized.
     */
    public void scale(double scale, boolean adjustOffsets, int hints)
    {
        for (int d = 0; d < getDirectionCount(); d++)
        {
            for (int f = 0; f < getFrameCount(); f++)
            {
                AnimationFrame af = getFrame(d, f);
                af.setImage(
                        ImageUtilities.bufferImage(
                                af.getImage().getScaledInstance(
                                        Math.max(1, (int) (af.getImage().getWidth() * scale)),
                                        Math.max(1, (int) (af.getImage().getHeight() * scale)),
                                        hints)));
                if (adjustOffsets)
                {
                    af.setXOffset((int) (af.getXOffset() * scale));
                    af.setYOffset((int) (af.getYOffset() * scale));
                }
            }
        }
    }

    /**
     * Trims any transparent space off of the borders of the images contained within this Animation file, compensating
     * by adjusting their frames' offsets.
     */
    public void trimBorders()
    {
        for (AnimationFrame frame : frameList)
        {
            BufferedImage image = frame.getImage();
            int offsetX = frame.getXOffset();
            int offsetY = frame.getYOffset();
            boolean worked = true;
            int[] line = new int[image.getWidth()];

            // trim image bottom
            while ((worked) && (image.getHeight() > 1))
            {
                worked = false;
                image.getRGB(0, image.getHeight() - 1, line.length, 1, line, 0, line.length);
                boolean allTransparent = true;
                for (int pixel : line)
                {
                    if ((pixel & 0xFF000000) != 0)
                    {
                        allTransparent = false;
                        break;
                    }
                }
                if (allTransparent)
                {
                    worked = true;
                    image = image.getSubimage(0, 0, image.getWidth(), image.getHeight() - 1);
                }
            }

            // trim image top
            worked = true;
            while ((worked) && (image.getHeight() > 1))
            {
                worked = false;
                image.getRGB(0, 0, line.length, 1, line, 0, line.length);
                boolean allTransparent = true;
                for (int pixel : line)
                {
                    if ((pixel & 0xFF000000) != 0)
                    {
                        allTransparent = false;
                        break;
                    }
                }
                if (allTransparent)
                {
                    worked = true;
                    image = image.getSubimage(0, 1, image.getWidth(), image.getHeight() - 1);
                    offsetY++;
                }
            }

            // trim right side
            worked = true;
            while ((worked) && (image.getWidth() > 1))
            {
                worked = false;
                boolean allTransparent = true;
                for (int j = 0; j < image.getHeight(); j++)
                {
                    if ((image.getRGB(image.getWidth() - 1, j) & 0xFF000000) != 0)
                    {
                        allTransparent = false;
                        break;
                    }
                }
                if (allTransparent)
                {
                    worked = true;
                    image = image.getSubimage(0, 0, image.getWidth() - 1, image.getHeight());
                }
            }

            // trim left side
            worked = true;
            while ((worked) && (image.getWidth() > 1))
            {
                worked = false;
                boolean allTransparent = true;
                for (int j = 0; j < image.getHeight(); j++)
                {
                    if ((image.getRGB(0, j) & 0xFF000000) != 0)
                    {
                        allTransparent = false;
                        break;
                    }
                }
                if (allTransparent)
                {
                    worked = true;
                    image = image.getSubimage(1, 0, image.getWidth() - 1, image.getHeight());
                    offsetX++;
                }
            }

            frame.setImage(image);
            frame.setXOffset(offsetX);
            frame.setYOffset(offsetY);
        }
    }

    /**
     * Sets the "base offset" to the provided coordinates.  This changes the position for the origin in the Animation's
     * virtual coordinate space.  For example, if this method is called using the parameters <code>30,30</code>, all
     * images in this Animation will be moved up and left by 30 pixels.  If called using the offsets of a frame, the
     * frame in question will have offsets of <code>(0,0)</code> and all of the other frames will be moved the same
     * amount.
     *
     * @param x The X coordinate of the base offset.
     * @param y The Y coordinate of the base offset.
     */
    public void adjustOffsets(int x, int y)
    {
        for (AnimationFrame frame : frameList)
        {
            frame.setXOffset(frame.getXOffset() - x);
            frame.setYOffset(frame.getYOffset() - y);
        }
    }

    /**
     * Retrieves the image contained within the provided frame, padded to reflect the virtual coordinate space.  The
     * size of the returned image is equal in size to the virtual coordinate space; the image from the specified frame
     * is drawn in position over that space.  The padding space will be clear.  The primary use of this method is to
     * transform the data stored within this {@link Animation} object into a form usable by standard image editors.
     *
     * @param direction The direction number from which to obtain the image.
     * @param frame     The frame number from which to obtain the image.
     */
    public BufferedImage getPaddedImage(int direction, int frame)
    {
        return getPaddedImage(direction, frame, AWTUtilities.COLOR_TRANSPARENT);
    }

    /**
     * Retrieves the image contained within the provided frame, padded to reflect the virtual coordinate space.  The
     * size of the returned image is equal in size to the virtual coordinate space; the image from the specified frame
     * is drawn in position over that space.  The primary use of this method is to transform the data stored within this
     * {@link Animation} object into a form usable by standard image editors.
     *
     * @param direction     The direction number from which to obtain the image.
     * @param frame         The frame number from which to obtain the image.
     * @param paddingColor The {@link Color} with which to pad the image.
     */
    public BufferedImage getPaddedImage(int direction, int frame, Color paddingColor)
    {
        int firstX = getFirstXIndex();
        int firstY = getFirstYIndex();
        BufferedImage ret = new BufferedImage(
                getLastXIndex() - firstX + 1,
                getLastYIndex() - firstY + 1,
                BufferedImage.TYPE_INT_ARGB);
        Graphics g = ret.getGraphics();
        g.setColor(paddingColor);
        g.fillRect(0, 0, ret.getWidth(), ret.getHeight());
        AnimationFrame f = getFrame(direction, frame);
        g.drawImage(f.getImage(), f.getXOffset() - firstX, f.getYOffset() - firstY, null);
        return ret;
    }

// STATIC METHODS ////////////////////////////////////////////////////////////////

}

// END OF FILE