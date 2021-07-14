package orioni.sixdice;

import orioni.jz.awt.image.RestrictableIndexColorModel;
import orioni.jz.common.exception.ParseException;
import orioni.jz.io.FileType;
import orioni.jz.io.files.FileUtilities;
import orioni.jz.util.Pair;
import orioni.jz.util.ProgressTracker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * This interface is intended to be implemented by any class which can read and write {@link Animation} objects from and
 * to a specific file format.
 *
 * @author Zachary Palmer
 */
public abstract class AnimationCodec
{
// CONSTANTS /////////////////////////////////////////////////////////////////////

    /**
     * An enumeration which lists the types of messages which can be returned by {@link
     * AnimationCodec#check(Animation)}.
     */
    public static enum MessageType
    {
        /**
         * A message type which indicates that the user should be informed of the message but the message is not fatal.
         */
        WARNING,
        /**
         * A message type which indicates that the {@link Animation} being checked cannot be written using this codec.
         */
        FATAL
    }

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

    /**
     * The index which this codec assumes is transparent.
     */
    protected int transparentIndex;

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * General constructor.
     */
    public AnimationCodec()
    {
        transparentIndex = 0;
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Retrieves the index that this codec assumes is transparent.
     *
     * @return The transparent index, or <code>-1</code> for no transparent index.
     */
    public int getTransparentIndex()
    {
        return transparentIndex;
    }

    /**
     * Sets the index that this codec assumes is transparent.
     *
     * @param index The transparent index, or <code>-1</code> for no transparent index.
     */
    public void setTransparentIndex(int index)
    {
        transparentIndex = index;
    }

    /**
     * Preprocesses a {@link RestrictableIndexColorModel} for the codec.  Since the {@link AnimationCodec} class has
     * support for transparent indices and other such common utilities, this method will derive a {@link
     * RestrictableIndexColorModel} appropriate for use by the codec according to the current {@link AnimationCodec}
     * settings.  Use of this method is not strictly necessary but does help streamline behavior.
     *
     * @param palette The {@link RestrictableIndexColorModel} to prepare.
     * @return The prepared {@link RestrictableIndexColorModel}.
     */
    public RestrictableIndexColorModel deriveCodecPalette(RestrictableIndexColorModel palette)
    {
        if (getTransparentIndex() != -1)
        {
            palette = palette.deriveWithTransparentInices(getTransparentIndex());
            palette.addRestrictedIndex(getTransparentIndex());
        }
        return palette;
    }

    /**
     * Returns the name of this {@link AnimationCodec}.
     *
     * @return A string describing this {@link AnimationCodec}.
     */
    public String toString()
    {
        return getName();
    }

    /**
     * Reads an {@link Animation} from the specified {@link File}.
     *
     * @param file    The {@link java.io.File} from which to read the {@link Animation} object.
     * @param palette The palette in which to render the image once it has been read, or <code>null</code> to indicate
     *                that the palette stored in the image file should be used.
     * @param tracker The {@link ProgressTracker} which will track the progress of the loading operation, or
     *                <code>null</code> if no {@link ProgressTracker} is desired.
     * @return The {@link Animation} which was read from the {@link File}.
     * @throws ParseException       If the provided {@link File} cannot be read by this codec.
     * @throws IOException          If an I/O error occurs while attempting to read the file.
     * @throws NullPointerException If <code>palette</code> is <code>null</code> and this codec reads files which do not
     *                              carry their own palettes.
     */
    public Animation read(File file, RestrictableIndexColorModel palette, ProgressTracker tracker)
            throws ParseException, IOException
    {
        if (tracker == null) tracker = new ProgressTracker(0, 1);
        return decode(FileUtilities.getFileContents(file), palette, tracker);
    }

    /**
     * Writes an {@link Animation} to the provided {@link File}.  This method should not be called unless a call to
     * {@link AnimationCodec#check(Animation)} using the same {@link Animation} object produces no messages with a
     * {@link MessageType#FATAL} type.  If the write fails, the partially written file will be deleted.
     *
     * @param file      The {@link File} to which to write the {@link Animation} object.
     * @param animation The {@link Animation} to write.
     * @param palette   The palette in which to write the animation, or <code>null</code> to allow the codec to choose
     *                  its own palette.  The latter option is not available unless {@link
     *                  AnimationCodec#formatContainsPalette()} is <code>true</code>.
     * @param tracker   The {@link ProgressTracker} which will track the progress of the saving operation, or
     *                  <code>null</code> if no {@link ProgressTracker} is desired.
     * @throws IOException          If an I/O error occurs while attempting to write the file.
     * @throws NullPointerException If <code>palette</code> is <code>null</code> and this codec writes files which do
     *                              not carry their own palettes.
     */
    public void write(File file, Animation animation, RestrictableIndexColorModel palette, ProgressTracker tracker)
            throws IOException
    {
        if (tracker == null) tracker = new ProgressTracker(0, 1);
        FileOutputStream fos = null;
        boolean opened = false;
        try
        {
            try
            {
                fos = new FileOutputStream(file);
                opened = true;
                fos.write(encode(animation, palette, tracker));
            } finally
            {
                if (fos != null)
                {
                    try
                    {
                        fos.close();
                    } catch (IOException e)
                    {
                    }
                }
            }
        } catch (IOException ioe)
        {
            if (opened) file.delete();
            throw ioe;
        }
    }

    /**
     * Decodes an {@link Animation} from the specified data array.  Most often, this will be a <code>byte[]</code> of
     * the entire file's contents.  The intention of this method is to contain all of the file I/O handling in the
     * {@link AnimationCodec} class.
     *
     * @param data    The <code>byte[]</code> containing the data to decode.
     * @param palette The palette in which to render the image once it has been read, or <code>null</code> to indicate
     *                that the palette stored in the image file should be used.
     * @param tracker The {@link ProgressTracker} which will track the progress of the decoding operation.
     * @return The decoded {@link Animation}.
     * @throws ParseException       If the provided data cannot be decoded by this codec.
     * @throws NullPointerException If <code>palette</code> is <code>null</code> and this codec reads files which do not
     *                              carry their own palettes.
     */
    public abstract Animation decode(byte[] data, RestrictableIndexColorModel palette, ProgressTracker tracker)
            throws ParseException;

    /**
     * Encodes an {@link Animation}, storing it in a <code>byte[]</code>.  Most often, this byte array will then be
     * immediately written to a file as its entire contents.  The intention of this method is to contain all of the file
     * I/O handling in the {@link AnimationCodec} class.
     *
     * @param animation The {@link Animation} to encode.
     * @param palette   The palette in which to encode the animation.
     * @param tracker   The {@link ProgressTracker} which will track the progress of the encoding operation.
     * @return The encoded data.
     * @throws NullPointerException If <code>palette</code> is <code>null</code> and this codec writes files which do
     *                              not carry their own palettes.
     */
    public abstract byte[] encode(Animation animation, RestrictableIndexColorModel palette, ProgressTracker tracker);

    /**
     * Checks the provided {@link Animation} object to ensure that it can be written in the format supported by this
     * codec.
     *
     * @param animation The {@link Animation} to examine.
     * @return A {@link List} of {@link Pair}<code>&lt;{@link String},{@link MessageType}&gt;</code> objects noting
     *         information about the {@link Animation}.
     */
    public abstract List<Pair<String, MessageType>> check(Animation animation);

    // Informational methods follow.

    /**
     * Retrieves a {@link FileType} object describing the types of files which can be used by this codec.  This
     * recommendation is provided to assist programs in recognizing files which will be valid to pass to {@link
     * AnimationCodec#read(java.io.File,RestrictableIndexColorModel)}.
     *
     * @return The {@link FileType} of files of the type used by this codec.
     */
    public abstract FileType getFileType();

    /**
     * Determines whether or not the format supported by this {@link AnimationCodec} contains its own palette.  If this
     * is not the case, the {@link AnimationCodec#read(java.io.File, orioni.jz.awt.image.RestrictableIndexColorModel,
     * ProgressTracker)} method must be provided a valid palette.
     *
     * @return <code>true</code> if the animation format contains its own palette; <code>false</code> otherwise.
     */
    public abstract boolean formatContainsPalette();

    /**
     * Retrieves a human-readable name which describes this {@link AnimationCodec}.  This is used for nothing more than
     * display purposes.
     *
     * @return The human-readable name of this codec.
     */
    public abstract String getName();
}

// END OF FILE