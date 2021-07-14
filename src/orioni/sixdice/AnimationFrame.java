package orioni.sixdice;

import java.awt.image.BufferedImage;

/**
 * This class is designed to contain information about a single animation frame: the image itself and the X and Y
 * offsets of the image in the animation's virtual coordinate space.
 *
 * @author Zachary Palmer
 */
public class AnimationFrame
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

    /**
     * The image in this frame.
     */
    protected BufferedImage image;
    /**
     * The X offset of the frame.
     */
    protected int offsetX;
    /**
     * The Y offset of the frame.
     */
    protected int offsetY;
    /**
     * The optional data for this {@link AnimationFrame}.  The information stored within is dependent upon application.
     */
    protected byte[] optionalData;

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * Skeleton constructor.  Creates an {@link AnimationFrame} with a blank 1x1 image and a (0,0) offset.
     */
    public AnimationFrame()
    {
        this(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 0, 0);
    }

    /**
     * General constructor.
     *
     * @param image    The image in this frame.
     * @param offsetX The X offset of the frame.
     * @param offsetY The Y offset of the frame.
     */
    public AnimationFrame(BufferedImage image, int offsetX, int offsetY)
    {
        this.image = image;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        optionalData = new byte[0];
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Retrieves the optional data array.  The returned value is the actual array, not a copy.
     * @return The optional data array.
     */
    public byte[] getOptionalData()
    {
        return optionalData;
    }

    /**
     * Sets the optional data array.
     * @param value The new value for the optional data array.
     */
    public void setOptionalData(byte[] value)
    {
        optionalData = value;
    }

    /**
     * Retrieves the image of this frame.
     *
     * @return The image of this frame.
     */
    public BufferedImage getImage()
    {
        return image;
    }

    /**
     * Retrieves the X offset of this frame.
     *
     * @return The X offset of this frame.
     */
    public int getXOffset()
    {
        return offsetX;
    }

    /**
     * Retrieves the Y offset of this frame.
     *
     * @return The Y offset of this frame.
     */
    public int getYOffset()
    {
        return offsetY;
    }

    /**
     * Changes the image for this {@link AnimationFrame}.
     *
     * @param image The image for this {@link AnimationFrame}.
     */
    public void setImage(BufferedImage image)
    {
        this.image = image;
    }

    /**
     * Changes the X offset for this {@link AnimationFrame}.
     *
     * @param offsetX The X offset for this {@link AnimationFrame}.
     */
    public void setXOffset(int offsetX)
    {
        this.offsetX = offsetX;
    }

    /**
     * Changes the Y offset for this {@link AnimationFrame}.
     *
     * @param offsetY The Y offset for this {@link AnimationFrame}.
     */
    public void setYOffset(int offsetY)
    {
        this.offsetY = offsetY;
    }

    /**
     * Generates a string describing this {@link AnimationFrame}.
     * @return A string describing this animation frame.
     */
    public String toString()
    {
        return image.getWidth()+"x"+image.getHeight()+" @ ("+offsetX +", "+offsetY +")";
    }

// STATIC METHODS ////////////////////////////////////////////////////////////////

}

// END OF FILE