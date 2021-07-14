package orioni.sixdice;

import orioni.jz.awt.*;
import orioni.jz.awt.image.ImageUtilities;
import orioni.jz.awt.image.RestrictableIndexColorModel;
import orioni.jz.awt.listener.DisposeOnHideListener;
import orioni.jz.awt.listener.DocumentAnyChangeListener;
import orioni.jz.awt.listener.PopupMenuMouseListener;
import orioni.jz.awt.swing.*;
import orioni.jz.awt.swing.action.ComponentTogglingAction;
import orioni.jz.awt.swing.action.ExecuteMethodAction;
import orioni.jz.awt.swing.convenience.ComponentConstructorPanel;
import orioni.jz.awt.swing.convenience.ContentConstructorTabbedPane;
import orioni.jz.awt.swing.convenience.SelfReturningJLabel;
import orioni.jz.awt.swing.convenience.SizeConstructorScrollPane;
import orioni.jz.awt.swing.dialog.ScrollableTextAndCheckboxDialog;
import orioni.jz.awt.swing.dialog.ScrollableTextDialog;
import orioni.jz.awt.swing.dialog.SwingErrorStreamDialog;
import orioni.jz.awt.swing.dialog.WaitingDialog;
import orioni.jz.awt.swing.icon.ColoredBlockIcon;
import orioni.jz.awt.swing.list.IntegerRangeListModel;
import orioni.jz.awt.swing.list.SortedListModel;
import orioni.jz.awt.swing.listener.WhatIsThisMouseListener;
import orioni.jz.awt.swing.popup.PopupOption;
import orioni.jz.awt.swing.popup.PopupOptionMenu;
import orioni.jz.io.FileType;
import orioni.jz.io.NullOutputStream;
import orioni.jz.io.StreamPipe;
import orioni.jz.io.files.FileExtensionFilter;
import orioni.jz.io.files.FileUtilities;
import orioni.jz.io.image.PCXReaderSpi;
import orioni.jz.io.image.PCXWriterSpi;
import orioni.jz.math.MathUtilities;
import orioni.jz.util.Pair;
import orioni.jz.util.ProgressTracker;
import orioni.jz.util.Utilities;
import orioni.jz.util.configuration.*;
import orioni.jz.util.strings.BoundedIntegerInterpreter;
import orioni.jz.util.strings.ColorInterpreter;
import orioni.jz.util.strings.StringUtilities;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * SixDice is a program designed to perform conversions between certain animation formats and various other graphics
 * formats.
 *
 * @author Zachary Palmer
 */
public class SixDiceFrame extends AbstractEditorFrame
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

    // HELP STRINGS //////////////////////////////////////////////////////////////

    /**
     * A string providing help on the default palette.
     */
    public static final String HELP_DEFAULT_PALETTE =
            "These animation files do not carry information about specific colors in them, but a general description " +
            "\"color 1 goes here, color 2 goes there\"; this is called an \"indexed color model.\"  Therefore, when " +
            "an animation is loaded, it must be associated with a palette before it can be displayed.  The provided " +
            "palettes are from Diablo II: Lord of Destruction v1.10 and animation files used by that program will be " +
            "displayed using one of these palettes.  To determine which palette your program should use, consult " +
            "the program's documentation or other informational resources.  For Diablo II: LoD, the " +
            "default \"Act 0\" palette is usually advisable.";

    // CONFIGURATION CONSTANTS ///////////////////////////////////////////////////

    /**
     * The configuration file for this program.
     */
    protected static final File CONFIGURATION_FILE = new File(
            System.getProperty("user.home") + File.separatorChar + ".sixdice.cfg");

    /**
     * The {@link ConfigurationElement} specifying whether or not the workspace is cleared before performing a
     * drag-and-drop load.
     */
    protected static final BooleanConfigurationElement CONFIG_ELEMENT_UI_CLEAR_ON_DND_IMPORTS =
            new BooleanConfigurationElement("ui.dnd.load.clear", true);
    /**
     * The {@link ConfigurationElement} for the background color of the image display.  This is the color which
     * surrounds the image in the event that the image panel is larger than the image itself.
     */
    protected static final ConfigurationElement<Color> CONFIG_ELEMENT_UI_IMAGE_BACKGROUND =
            new ConfigurationElement<Color>(
                    "ui.image.background",
                    ColorInterpreter.SINGLETON,
                    new Color(96, 96, 96));
    /**
     * The {@link ConfigurationElement} for the underlay color of the image display.  This is the color which appears
     * under the image and will show through transparent segments.
     */
    protected static final ConfigurationElement<Color> CONFIG_ELEMENT_UI_IMAGE_UNDERLAY =
            new ConfigurationElement<Color>(
                    "ui.image.underlay",
                    ColorInterpreter.SINGLETON,
                    Color.BLACK);
    /**
     * The {@link ConfigurationElement} specifying the layout of the {@link SixDice} frame.
     */
    protected static final BoundedIntegerConfigurationElement CONFIG_ELEMENT_UI_LAYOUT =
            new BoundedIntegerConfigurationElement("ui.layout", 0, 0, 3);
    /**
     * The {@link ConfigurationElement} which states whether or not the image is currently displayed in the selected
     * palette.
     */
    protected static final BooleanConfigurationElement CONFIG_ELEMENT_UI_IMAGE_RENDERED_IN_PALETTE =
            new BooleanConfigurationElement("ui.image.paletterendered", true);

    /**
     * The {@link ConfigurationElement} which states that, after a save, the saved file is reloaded.
     */
    protected static final BooleanConfigurationElement CONFIG_ELEMENT_UI_SAVED_FILES_RELOADED =
            new BooleanConfigurationElement("ui.loadonsave", false);

    /**
     * The {@link ConfigurationElement} for the selected palette in the configuration object.
     */
    protected static final StringConfigurationElement CONFIG_ELEMENT_SELECTED_PALETTE =
            new StringConfigurationElement("palette.selected", "Act 0");
    /**
     * The {@link ConfigurationElement} for the last successful import or export's selected file filter in terms of the
     * file extension it represents.
     */
    protected static final StringConfigurationElement CONFIG_ELEMENT_PORT_FILTER =
            new StringConfigurationElement("port.filter", null);
    /**
     * The {@link ConfigurationElement} for the seperator string in filenames used to identify a series of images which
     * should be treated as a single animation.
     */
    protected static final StringConfigurationElement CONFIG_ELEMENT_PORT_SEPARATOR =
            new StringConfigurationElement("port.separator", "__");

    /**
     * The {@link ConfigurationElement} determining if the warning about palettes should be shown.
     */
    protected static final BooleanConfigurationElement CONFIG_ELEMENT_DISPLAY_PALETTE_WARNING =
            new BooleanConfigurationElement("ui.image.palette.warning", true);

    /**
     * The {@link ConfigurationElement} for the path of the last batch conversion.
     */
    protected static final StringConfigurationElement CONFIG_ELEMENT_BATCH_CONVERSION_PATH =
            new StringConfigurationElement("batch.path", System.getProperty("user.home"));
    /**
     * The {@link ConfigurationElement} for whether or not the batch conversion is recursive.
     */
    protected static final BooleanConfigurationElement CONFIG_ELEMENT_BATCH_CONVERSION_RECURSIVE =
            new BooleanConfigurationElement("batch.recursive", false);
    /**
     * The {@link ConfigurationElement} for the selected palette in the batch conversion dialog.
     */
    protected static final StringConfigurationElement CONFIG_ELEMENT_BATCH_CONVERSION_PALETTE =
            new StringConfigurationElement("batch.palette", "Act 0");
    /**
     * The {@link ConfigurationElement} containing the last-selected index for a source type in the batch conversion
     * dialog.
     */
    protected static final IntegerConfigurationElement CONFIG_ELEMENT_BATCH_CONVERSION_SOURCE_INDEX =
            new IntegerConfigurationElement("batch.sourceindex", 0);
    /**
     * The {@link ConfigurationElement} containing the last-selected index for a target type in the batch conversion
     * dialog.
     */
    protected static final IntegerConfigurationElement CONFIG_ELEMENT_BATCH_CONVERSION_TARGET_INDEX =
            new IntegerConfigurationElement("batch.targetindex", 0);

    /**
     * The {@link ConfigurationElement} for the import clear color.
     */
    protected static final ConfigurationElement<Color> CONFIG_ELEMENT_IMPORT_VIRTUAL_CLEAR_COLOR =
            new ConfigurationElement<Color>("import.color.virtualclear", ColorInterpreter.SINGLETON, Color.BLACK);
    /**
     * The {@link ConfigurationElement} determining if clear should be replaced by the virtual clear color on export.
     */
    protected static final BooleanConfigurationElement CONFIG_ELEMENT_EXPORT_CLEAR_TO_VIRTUAL =
            new BooleanConfigurationElement("export.color.virtualclear.translate", true);

    /**
     * The {@link ConfigurationElement} determining if frames are inserted or overwritten when a frame is imported.
     * <code>true</code> indicates an insert.
     */
    protected static final BooleanConfigurationElement CONFIG_ELEMENT_IMPORT_INSERTS =
            new BooleanConfigurationElement("import.insert", false);

    /**
     * The {@link ConfigurationElement} determining if frames are inserted or overwritten when a frame is being split.
     * <code>true</code> indicates an insert.
     */
    protected static final BooleanConfigurationElement CONFIG_ELEMENT_FRAME_SPLIT_INSERTS =
            new BooleanConfigurationElement("frame.split.insert", false);

    /**
     * The {@link ConfigurationElement} determining the clear index for the DC6 codec.
     */
    protected static final BoundedIntegerConfigurationElement CONFIG_ELEMENT_CODEC_DC6_CLEAR_INDEX =
            new BoundedIntegerConfigurationElement("codec.dc6.index.clear", 0, 0, 255);

    /**
     * The {@link ConfigurationElement} determining the clear index for the DCC codec.
     */
    protected static final BoundedIntegerConfigurationElement CONFIG_ELEMENT_CODEC_DCC_CLEAR_INDEX =
            new BoundedIntegerConfigurationElement("codec.dc6.index.clear", 0, 0, 255);

    // OTHER CONSTANTS ///////////////////////////////////////////////////////////

    /**
     * The name of the splash file (not including path).
     */
    public static final String SPLASH_FILE_NAME =
            "SixDice-Splash-v" + (SixDice.VERSION_STRING.endsWith("b") ?
                                  SixDice.VERSION_STRING.substring(0, SixDice.VERSION_STRING.length() - 1) :
                                  SixDice.VERSION_STRING) + ".png";

    /**
     * The minimum zoom percentage on the zoom slider.
     */
    public static final int ZOOM_SLIDER_MINIMUM_PERCENT = 1;
    /**
     * The maximum zoom percentage on the zoom slider.
     */
    public static final int ZOOM_SLIDER_MAXIMUM_PERCENT = 800;

// STATIC INITIALIZER ////////////////////////////////////////////////////////////

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

    /**
     * The component in which the current image is being drawn.
     */
    protected CenteredImageComponent imageComponent;
    /**
     * The {@link JScrollPane} used to display the {@link CenteredImageComponent}.
     */
    protected JScrollPane imageScrollPane;

    /**
     * The list from which the current frame number is selected.
     */
    protected JList frameList;
    /**
     * The list from which the current direction number is selected.
     */
    protected JList directionList;
    /**
     * The add button for the direction list.
     */
    protected JButton directionAddButton;
    /**
     * The insert button for the direction list.
     */
    protected JButton directionInsertButton;
    /**
     * The remove button for the direction list.
     */
    protected JButton directionRemoveButton;
    /**
     * The add button for the frame list.
     */
    protected JButton frameAddButton;
    /**
     * The insert button for the frame list.
     */
    protected JButton frameInsertButton;
    /**
     * The remove button for the frame list.
     */
    protected JButton frameRemoveButton;

    /**
     * The label containing the width of the currently-selected frame.
     */
    protected JLabel labelWidth;
    /**
     * The label containing the height of the currently-selected frame.
     */
    protected JLabel labelHeight;
    /**
     * The field containing the X offset of the currently-selected frame.
     */
    protected JTextField offsetX;
    /**
     * The field containing the Y offset of the currently-selected frame.
     */
    protected JTextField offsetY;

    /**
     * The {@link JComboBox} containing default palettes.
     */
    protected JComboBox paletteBox;
    /**
     * The {@link JCheckBox} which states whether or not forced palette rendering is used.
     */
    protected JCheckBox paletteRendering;

    /**
     * The zoom slider for the display component.
     */
    protected JSlider zoomSlider;
    /**
     * The {@link JTextField} used to display and edit the current zoom level.
     */
    protected JTextField zoomField;

    /**
     * The splash screen image.
     */
    protected BufferedImage splashImage;
    /**
     * The {@link SixDiceCore} which backs this UI with actual {@link Animation}-editing functionality.
     */
    protected SixDiceCore core;

    /**
     * The {@link OptionallyPaintedPanel} with the warnings display on it.
     */
    protected OptionallyPaintedPanel warningsPanel;

    /**
     * The {@link DC6Codec} used by this program to decode DC6 files.
     */
    protected DC6Codec dc6Codec;
    /**
     * An array containing the codecs used by this program.
     */
    protected AnimationCodec[] codecs;

    /**
     * A {@link DelayedDialogDisplayer} which displays a {@link WaitingDialog} centered over this component.
     */
    protected DelayedDialogDisplayer waitingDialogDisplayer;
    /**
     * The {@link JProgressBar} used by the waiting dialog.
     */
    protected JProgressBar waitingDialogProgressBar;

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * General constructor.
     */
    public SixDiceFrame()
    {
        super(CONFIGURATION_FILE, "SixDice v" + SixDice.VERSION_STRING);
        final SixDiceFrame scopedThis = this;
        PCXReaderSpi.register();
        PCXWriterSpi.register();

        configuration.addElements(
                CONFIG_ELEMENT_UI_CLEAR_ON_DND_IMPORTS,
                CONFIG_ELEMENT_UI_IMAGE_BACKGROUND,
                CONFIG_ELEMENT_UI_IMAGE_UNDERLAY,
                CONFIG_ELEMENT_UI_LAYOUT,
                CONFIG_ELEMENT_UI_IMAGE_RENDERED_IN_PALETTE,
                CONFIG_ELEMENT_UI_SAVED_FILES_RELOADED,
                CONFIG_ELEMENT_SELECTED_PALETTE,
                CONFIG_ELEMENT_PORT_FILTER,
                CONFIG_ELEMENT_PORT_SEPARATOR,
                CONFIG_ELEMENT_DISPLAY_PALETTE_WARNING,
                CONFIG_ELEMENT_BATCH_CONVERSION_PATH,
                CONFIG_ELEMENT_BATCH_CONVERSION_RECURSIVE,
                CONFIG_ELEMENT_BATCH_CONVERSION_PALETTE,
                CONFIG_ELEMENT_BATCH_CONVERSION_SOURCE_INDEX,
                CONFIG_ELEMENT_BATCH_CONVERSION_TARGET_INDEX,
                CONFIG_ELEMENT_IMPORT_VIRTUAL_CLEAR_COLOR,
                CONFIG_ELEMENT_EXPORT_CLEAR_TO_VIRTUAL,
                CONFIG_ELEMENT_IMPORT_INSERTS,
                CONFIG_ELEMENT_FRAME_SPLIT_INSERTS,
                CONFIG_ELEMENT_CODEC_DC6_CLEAR_INDEX,
                CONFIG_ELEMENT_CODEC_DCC_CLEAR_INDEX);

        final SwingErrorStreamDialog sesd = new SwingErrorStreamDialog(null, "SixDice Error Stream Report");
        sesd.setLocation(0, 0);
        final WaitingDialog wd = new WaitingDialog(this);
        waitingDialogProgressBar = wd.getProgressBar();
        waitingDialogDisplayer = new DelayedDialogDisplayer(wd)
        {
            public synchronized void setVisible(boolean visible)
            {
                dialog.setLocationRelativeTo(scopedThis);
                super.setVisible(visible);
            }
        };

        this.addWindowListener(
                new WindowAdapter()
                {
                    public void windowClosed(WindowEvent e)
                    {
                        sesd.dispose();
                        wd.dispose();
                    }
                });

        dc6Codec = new DC6Codec();
        codecs = new AnimationCodec[]{dc6Codec, DCCCodec.SINGLETON};

        core = new SixDiceCore(codecs);

        int insertionIndex = extendedMenuBar.getExtendedMenu("File").getIndexOfSeperator(0);
        extendedMenuBar.insertSeparator("File", insertionIndex);
        insertionIndex++;
        extendedMenuBar.add(
                "File", "Import", insertionIndex,
                new ExecuteMethodAction(this, "performMenuImport"),
                KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK));
        insertionIndex++;
        extendedMenuBar.add(
                "File", "Import Series", insertionIndex,
                new ExecuteMethodAction(this, "performMenuImportSeries"),
                KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
        insertionIndex++;
        extendedMenuBar.add(
                "File", "Export", insertionIndex,
                new ExecuteMethodAction(this, "performMenuExport"),
                KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK));
        insertionIndex++;
        extendedMenuBar.add(
                "File", "Export Series", insertionIndex,
                new ExecuteMethodAction(this, "performMenuExportSeries"),
                KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK));

        extendedMenuBar.add(
                "Image", "Batch Convert",
                new ExecuteMethodAction(this, "performMenuBatchConvert", true),
                KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK));
        extendedMenuBar.addSeparator("Image");
        extendedMenuBar.add(
                "Image", "Trim Borders",
                new ExecuteMethodAction(this, "performMenuTrimBorders"),
                KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK));
        extendedMenuBar.add(
                "Image", "Adjust Offsets",
                new ExecuteMethodAction(this, "performMenuAdjustOffset"),
                KeyStroke.getKeyStroke(KeyEvent.VK_J, KeyEvent.CTRL_DOWN_MASK));
        extendedMenuBar.add(
                "Image", "Center Offset",
                new ExecuteMethodAction(this, "performMenuCenterOffset"),
                KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK));
        extendedMenuBar.addSeparator("Image");
        extendedMenuBar.add(
                "Image", "Split Frame",
                new ExecuteMethodAction(this, "performMenuSplitFrame"),
                KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK));
        extendedMenuBar.add(
                "Image", "Join Frames",
                new ExecuteMethodAction(this, "performMenuJoinFrames"),
                KeyStroke.getKeyStroke(KeyEvent.VK_J, KeyEvent.CTRL_DOWN_MASK));
        extendedMenuBar.addSeparator("Image");
        extendedMenuBar.add(
                "Image", "Color Change",
                new ExecuteMethodAction(this, "performMenuColorChange"),
                KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
        extendedMenuBar.add(
                "Image", "Resize Images",
                new ExecuteMethodAction(this, "performMenuResize"),
                KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));

        extendedMenuBar.add("Options", "Preferences", new ExecuteMethodAction(this, "performMenuPreferences"));

        extendedMenuBar.add("Help", "Quick Tips").addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        new Thread("Quick Tips Display")
                        {
                            public void run()
                            {
                                JOptionPane.showMessageDialog(
                                        scopedThis,
                                        "<html>The following pieces of information may be useful when learning how " +
                                        "to use SixDice:<ul><li>Right-clicking on most interface components " +
                                        "(buttons, lists, checkboxes, etc.) will provide a \"What Is This?\"<br>" +
                                        "button that you can use to learn more about that interface component's " +
                                        "function.</li><li>SixDice can be run as a command-line application.  Try " +
                                        "running SixDice with the parameter \"-h\" for <br>more information (on most " +
                                        "systems, \"java -jar SixDice.jar -h\").</li></ul>Thanks for trying SixDice." +
                                        "&nbsp;  :)  Happy modding!",
                                        "Quick Tips", JOptionPane.INFORMATION_MESSAGE);
                            }
                        }.start();
                    }
                });
        extendedMenuBar.add("Help", "About").addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        new Thread("Vanity Display")
                        {
                            public void run()
                            {
                                JOptionPane.showMessageDialog(
                                        scopedThis, "<html><center><font face=\"Sans Serif\"><b>SixDice v" +
                                                    SixDice.VERSION_STRING +
                                                    "<br>by Zachary Palmer<br>zep01@bahj.com<br><br>" +
                                                    "(c)2005, All Rights Reserved",
                                        "About SixDice",
                                        JOptionPane.INFORMATION_MESSAGE);
                            }
                        }.start();
                    }
                });

        extendedMenuBar.autoAssignAllMnemonics();

        // Build frame components
        splashImage = null;
        try
        {
            URL url = getClass().getResource("/media/" + SPLASH_FILE_NAME);
            if (url != null) splashImage = ImageIO.read(url);
        } catch (IOException e)
        {
            // No splash image.  Oh well.
        }
        if (splashImage == null)
        {
            try
            {
                splashImage =
                        ImageIO.read(
                                new File("." + File.separatorChar + "media" + File.separatorChar + SPLASH_FILE_NAME));
            } catch (IOException e)
            {
                // No splash image again.  Oh well.
            }
        }
        if (SixDice.VERSION_STRING.endsWith("b"))
        {
            if (splashImage == null)
            {
                splashImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
            }
            splashImage = ImageUtilities.adjustHSB(splashImage, 0.0, -1, -0.5);
            Graphics g = splashImage.getGraphics();
            g.setColor(Color.WHITE);
            g.translate(splashImage.getWidth() / 2, splashImage.getHeight() / 2);
            if (g instanceof Graphics2D)
            {
                ((Graphics2D) g).rotate(Math.PI / 6);
            }
            JLabel label = new JLabel("BETA");
            label.setForeground(Color.WHITE);
            label.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 80));
            label.setSize(label.getPreferredSize());
            g.translate(-label.getWidth() / 2, -label.getHeight() / 2);
            label.paint(g);
        }

        imageComponent = new CenteredImageComponent(
                splashImage, configuration.getValue(CONFIG_ELEMENT_UI_IMAGE_BACKGROUND))
        {
            public void setImage(Image image)
            {
                if ((zoomSlider == null) || (zoomSlider.getValue() == 100))
                {
                    super.setImage(image);
                } else
                {
                    double scale = zoomSlider.getValue() / 100.0;
                    super.setImage(
                            image.getScaledInstance(
                                    Math.max(1, (int) (image.getWidth(null) * scale)),
                                    Math.max(1, (int) (image.getHeight(null) * scale)),
                                    Image.SCALE_REPLICATE));
                    doLayout();
                }
            }
        };
        imageComponent.setPreferredSize(new Dimension(0, 0));
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.setAlignmentX(Component.RIGHT_ALIGNMENT);
        directionList = new JList(new IntegerRangeListModel(0, 0));
        directionList.setCellRenderer(renderer);
        directionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        frameList = new JList(new IntegerRangeListModel(0, 0));
        frameList.setCellRenderer(renderer);
        frameList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        labelWidth = new JLabel("0");
        labelHeight = new JLabel("0");
        labelWidth.setAlignmentX(Component.RIGHT_ALIGNMENT);
        labelHeight.setAlignmentX(Component.RIGHT_ALIGNMENT);
        offsetX = new JTextField("0", 3);
        offsetY = new JTextField("0", 3);
        offsetX.setHorizontalAlignment(JTextField.RIGHT);
        offsetY.setHorizontalAlignment(JTextField.RIGHT);
        SortedListModel paletteBoxModel = new SortedListModel(
                Diablo2DefaultPalettes.PALETTE_MAP.keySet().toArray(),
                true);
        paletteBox = new JComboBox(paletteBoxModel);
        paletteRendering = new JCheckBox(
                " Use Palette Rendering?", configuration.getValue(CONFIG_ELEMENT_UI_IMAGE_RENDERED_IN_PALETTE));
        paletteRendering.setHorizontalAlignment(SwingConstants.TRAILING);
        zoomSlider = new JSlider(JSlider.HORIZONTAL, ZOOM_SLIDER_MINIMUM_PERCENT, ZOOM_SLIDER_MAXIMUM_PERCENT, 100);
        zoomField = new JTextField("100", 4);
        directionAddButton = new JButton("Add");
        directionInsertButton = new JButton("Ins");
        directionRemoveButton = new JButton("Rem");
        frameAddButton = new JButton("Add");
        frameInsertButton = new JButton("Ins");
        frameRemoveButton = new JButton("Rem");
        final JButton showWarningsButton = new JButton("Show Warnings");
        warningsPanel = new OptionallyPaintedPanel(
                new SpongyLayout(SpongyLayout.Orientation.VERTICAL, 2, 2, false, true))
        {
            public void setPainted(boolean painted)
            {
                showWarningsButton.setEnabled(painted);
                super.setPainted(painted);
            }
        };
        showWarningsButton.setEnabled(false);
        warningsPanel.add(
                new SelfReturningJLabel("There are warnings.").
                        setForegroundAndReturn(new Color(192, 96, 0)).
                        setFontAndReturn(new Font("Dialog", Font.BOLD, 12)));
        warningsPanel.add(new SpacingComponent(10, 10));
        warningsPanel.add(showWarningsButton);
        warningsPanel.setPainted(false);

        // Add what's this listeners
        imageComponent.addMouseListener(
                new WhatIsThisMouseListener(
                        "Image Preview",
                        "This portion of the SixDice screen allows you to see one frame of the animation you are " +
                        "currently using.  To determine which frame, find the \"Direction\" and \"Frame\" lists."));
        directionList.addMouseListener(
                new WhatIsThisMouseListener(
                        "Direction List",
                        "An animation file is composed of a number of directions (presumably in which some object " +
                        "moves) and a number of frames per direction.  The highlighted item in this list determines " +
                        "which direction you are currently viewing.  In combination with the frame list, this allows " +
                        "SixDice to pick one image out of the animation file to show you, along with its offset " +
                        "information."));
        frameList.addMouseListener(
                new PopupMenuMouseListener()
                {
                    public JPopupMenu getPopupMenu(MouseEvent e)
                    {
                        return new PopupOptionMenu(
                                frameList.getSelectedIndex(),
                                new PopupOption[]
                                        {
                                                new PopupOption("What Is This?")
                                                {
                                                    public void execute(Object selection)
                                                    {
                                                        ScrollableTextDialog.displayMessage(
                                                                scopedThis, "What Is This?",
                                                                "Frame List",
                                                                "An animation file is composed of a number of directions " +
                                                                "(presumably in which some object moves) and a number of frames " +
                                                                "per direction.  The highlighted item in this list determines " +
                                                                "which frame is showing in the current direction.  To determine " +
                                                                "which direction is being used, find the direction list.");
                                                    }
                                                },
                                                new PopupOption("Join Frames")
                                                {
                                                    public void execute(Object selection)
                                                    {
                                                        performMenuJoinFrames();
                                                    }
                                                },
                                        });
                    }
                });
        labelWidth.addMouseListener(
                new WhatIsThisMouseListener(
                        "Image Width",
                        "This number represents the width of the image currently being shown."));
        labelHeight.addMouseListener(
                new WhatIsThisMouseListener(
                        "Image Height",
                        "This number represents the height of the image currently being shown."));
        offsetX.addMouseListener(
                new WhatIsThisMouseListener(
                        "Image X Offset",
                        "Images in a single direction in an animation are usually meant to be animated together.  " +
                        "This number describes where this particular frame's image should be drawn on the screen " +
                        "relative to some other location.  For example, if the X offset of frame A is 5 and the X " +
                        "offset of frame B is 10, frame B will be displayed to the right of frame A when the " +
                        "animation is displayed in an animation program."));
        offsetY.addMouseListener(
                new WhatIsThisMouseListener(
                        "Image Y Offset",
                        "Images in a single direction in an animation file are usually meant to be animated " +
                        "together.  This number describes where this particular frame's image should be drawn on the " +
                        "screen relative to some other location.  Animation files invert the Y offsets, so a lower Y " +
                        "offset number means that the image will be displayed higher on the screen.  For example, if " +
                        "the Y offset of frame A is 5 and the Y offset of frame B is 10, frame B will be displayed " +
                        "below frame A in an animation program."));
        paletteBox.addMouseListener(new WhatIsThisMouseListener("Default Palette", HELP_DEFAULT_PALETTE));

        int index = paletteBoxModel.getIndexOf(configuration.getValue(CONFIG_ELEMENT_SELECTED_PALETTE));
        if (index == -1) index = 0;
        paletteBox.setSelectedIndex(index);

        // Build frame
        applyConfiguration(true);

        // ******* Add listeners
        // Update display whenever list selection is changed
        directionList.addListSelectionListener(
                new ListSelectionListener()
                {
                    public void valueChanged(ListSelectionEvent e)
                    {
                        updateDisplay();
                    }
                });
        frameList.addListSelectionListener(
                new ListSelectionListener()
                {
                    public void valueChanged(ListSelectionEvent e)
                    {
                        updateDisplay();
                    }
                });

        // Perform appropriate operations when user presses a list control button
        directionAddButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        if (core.getAnimation() == null)
                        {
                            JOptionPane.showMessageDialog(
                                    scopedThis, "No animation currently loaded.", "No Animation Loaded",
                                    JOptionPane.ERROR_MESSAGE);
                        } else
                        {
                            core.getAnimation().addDirection(core.getAnimation().getDirectionCount());
                            ((IntegerRangeListModel) (directionList.getModel())).setMaximum(
                                    core.getAnimation().getDirectionCount());
                            directionList.setSelectedIndex(core.getAnimation().getDirectionCount() - 1);
                            contentsUpdated();
                            updateDisplay();
                        }
                    }
                });
        directionInsertButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        if (core.getAnimation() == null)
                        {
                            JOptionPane.showMessageDialog(
                                    scopedThis, "No animation currently loaded.", "No Animation Loaded",
                                    JOptionPane.ERROR_MESSAGE);
                        } else
                        {
                            if (directionList.getSelectedIndex() == -1)
                            {
                                JOptionPane.showMessageDialog(
                                        scopedThis, "You must select a direction.  The new direction " +
                                                    "will be inserted above the selected one.",
                                        "No Direction Selected", JOptionPane.ERROR_MESSAGE);
                            } else
                            {
                                core.getAnimation().addDirection(directionList.getSelectedIndex());
                                ((IntegerRangeListModel) (directionList.getModel())).setMaximum(
                                        core.getAnimation().getDirectionCount());
                                contentsUpdated();
                                updateDisplay();
                            }
                        }
                    }
                });
        directionRemoveButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        performDirectionRemove(directionList.getSelectedIndices());
                    }
                });
        frameAddButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        if (core.getAnimation() == null)
                        {
                            JOptionPane.showMessageDialog(
                                    scopedThis, "No animation currently loaded.", "No Animation Loaded",
                                    JOptionPane.ERROR_MESSAGE);
                        } else
                        {
                            core.getAnimation().addFrame(core.getAnimation().getFrameCount());
                            ((IntegerRangeListModel) (frameList.getModel())).setMaximum(
                                    core.getAnimation().getFrameCount());
                            frameList.setSelectedIndex(core.getAnimation().getFrameCount() - 1);
                            contentsUpdated();
                            updateDisplay();
                        }
                    }
                });
        frameInsertButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        if (core.getAnimation() == null)
                        {
                            JOptionPane.showMessageDialog(
                                    scopedThis, "No animation currently loaded.", "No Animation Loaded",
                                    JOptionPane.ERROR_MESSAGE);
                        } else
                        {
                            if (frameList.getSelectedIndex() == -1)
                            {
                                JOptionPane.showMessageDialog(
                                        scopedThis, "You must select a frame.  The new frame " +
                                                    "will be inserted above the selected one.",
                                        "No Frame Selected", JOptionPane.ERROR_MESSAGE);
                            } else
                            {
                                core.getAnimation().addFrame(frameList.getSelectedIndex());
                                ((IntegerRangeListModel) (frameList.getModel())).setMaximum(
                                        core.getAnimation().getFrameCount());
                                contentsUpdated();
                                updateDisplay();
                            }
                        }
                    }
                });
        frameRemoveButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        if (core.getAnimation() == null)
                        {
                            JOptionPane.showMessageDialog(
                                    scopedThis, "No animation currently loaded.", "No Animation Loaded",
                                    JOptionPane.ERROR_MESSAGE);
                        } else
                        {
                            if (frameList.getSelectedIndex() == -1)
                            {
                                JOptionPane.showMessageDialog(
                                        scopedThis, "You must select a frame to remove.",
                                        "No Frame Selected", JOptionPane.ERROR_MESSAGE);
                            } else
                            {
                                boolean confirm = false;
                                for (int i = 0; i < core.getAnimation().getDirectionCount(); i++)
                                {
                                    BufferedImage image = core.getAnimation().getFrame(
                                            i, frameList.getSelectedIndex())
                                            .getImage();
                                    if ((image.getWidth() > 1) || (image.getHeight() > 1))
                                    {
                                        confirm = true;
                                        break;
                                    }
                                }
                                if ((!confirm) ||
                                    (JOptionPane.showConfirmDialog(
                                            scopedThis,
                                            "<html>Are you sure you want to delete all of the images in that " +
                                            "frame for each direction?<br>This operation cannot be undone.",
                                            "Are You Sure?",
                                            JOptionPane.YES_NO_OPTION,
                                            JOptionPane.QUESTION_MESSAGE) ==
                                                                          JOptionPane.YES_OPTION))
                                {
                                    int[] frames = frameList.getSelectedIndices();
                                    for (int i = frames.length - 1; i >= 0; i--)
                                    {
                                        core.getAnimation().removeFrame(frames[i]);
                                    }
                                    ((IntegerRangeListModel) (frameList.getModel())).setMaximum(
                                            core.getAnimation().getFrameCount());
                                    contentsUpdated();
                                    frameList.setSelectedIndex(
                                            Math.min(
                                                    core.getAnimation().getFrameCount() - 1,
                                                    frameList.getSelectedIndex()));
                                    updateDisplay();
                                }
                            }
                        }
                    }
                });

        // Update the palette for the animation whenever the user changes the palette selection
        paletteBox.addItemListener(
                new ItemListener()
                {
                    public void itemStateChanged(ItemEvent e)
                    {
                        if ((e.getStateChange() == ItemEvent.SELECTED) && (core.getAnimation() != null))
                        {
                            if (configuration.getValue(CONFIG_ELEMENT_DISPLAY_PALETTE_WARNING))
                            {
                                new Thread("Palette Change Notice Display")
                                {
                                    public void run()
                                    {
                                        if (ScrollableTextAndCheckboxDialog.displayMessage(
                                                scopedThis, "Palette Change Notice",
                                                "Notice about changing file palettes:",
                                                "Unlike many applications, SixDice does not simply change the " +
                                                "palette in which the image data is handled; it actually filters " +
                                                "each image using the specified palette.  This means that if, for " +
                                                "example, a certain animation were saved in one palette and then " +
                                                "loaded and saved in another, the quality of the image would be " +
                                                "reduced (similar to that which happens when an image is resized " +
                                                "several times).  It is important to recognize this, as the quality " +
                                                "loss may be minimal at first.\n\nWhen an image is imported, " +
                                                "however, it is stored in verbatim form until it is saved (and, " +
                                                "depending on your settings, afterward as well).  This means that " +
                                                "the palette can be changed after an image is imported without any " +
                                                "image distortion or loss of quality.",
                                                "Do not display this message again.",
                                                false))
                                        {
                                            configuration.setValue(CONFIG_ELEMENT_DISPLAY_PALETTE_WARNING, false);
                                        }
                                    }
                                }.start();
                            }
                            updateDisplay();
                        }
                    }
                });
        paletteRendering.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        configuration.setValue(
                                CONFIG_ELEMENT_UI_IMAGE_RENDERED_IN_PALETTE, paletteRendering.isSelected());
                        updateDisplay();
                    }
                });
        // Handle the zoom components
        zoomField.getDocument().addDocumentListener(
                new DocumentAnyChangeListener()
                {
                    public void documentChanged(DocumentEvent e)
                    {
                        String s = "";
                        try
                        {
                            s = e.getDocument().getText(0, e.getDocument().getLength());
                        } catch (BadLocationException e1)
                        {
                        }
                        if (s.length() == 0) return;
                        try
                        {
                            zoomSlider.setValue(Integer.parseInt(s));
                        } catch (NumberFormatException e1)
                        {
                            EventQueue.invokeLater(
                                    new Runnable()
                                    {
                                        public void run()
                                        {
                                            JOptionPane.showMessageDialog(
                                                    scopedThis, "Zoom value must be a positive integer.",
                                                    "Invalid Zoom", JOptionPane.ERROR_MESSAGE);
                                        }
                                    });
                        }
                    }
                });
        zoomSlider.addChangeListener(
                new ChangeListener()
                {
                    public void stateChanged(ChangeEvent e)
                    {
                        EventQueue.invokeLater(
                                new Runnable()
                                {
                                    public void run()
                                    {
                                        zoomField.setText(Integer.toString(zoomSlider.getValue()));
                                    }
                                });
                    }
                });
        zoomSlider.addChangeListener(
                new ChangeListener()
                {
                    public void stateChanged(ChangeEvent e)
                    {
                        updateDisplay();
                    }
                });

        // Update the animation content whenever the content of the offset box is changed
        offsetX.addFocusListener(
                new FocusAdapter()
                {
                    public void focusLost(FocusEvent e)
                    {
                        if ((core.getAnimation() != null) &&
                            (directionList.getSelectedIndex() != -1) &&
                            (frameList.getSelectedIndex() != -1))
                        {
                            AnimationFrame frame = core.getAnimation().getFrame(
                                    directionList.getSelectedIndex(),
                                    frameList.getSelectedIndex());
                            try
                            {
                                frame.setXOffset(Integer.parseInt(offsetX.getText()));
                            } catch (NumberFormatException e1)
                            {
                                JOptionPane.showMessageDialog(
                                        scopedThis, "The contents of the X-Offset field must be an integer.",
                                        "Not An Integer", JOptionPane.ERROR_MESSAGE);
                                offsetX.setText(Integer.toString(frame.getXOffset()));
                                offsetX.requestFocus();
                            }
                        }
                    }
                });
        offsetX.getDocument().addDocumentListener(
                new DocumentListener()
                {
                    public void removeUpdate(DocumentEvent e)
                    {
                        performUpdate();
                    }

                    public void insertUpdate(DocumentEvent e)
                    {
                        performUpdate();
                    }

                    public void changedUpdate(DocumentEvent e)
                    {
                        performUpdate();
                    }

                    private void performUpdate()
                    {
                        if ((core.getAnimation() != null) &&
                            (directionList.getSelectedIndex() != -1) &&
                            (frameList.getSelectedIndex() != -1))
                        {
                            AnimationFrame frame = core.getAnimation().getFrame(
                                    directionList.getSelectedIndex(),
                                    frameList.getSelectedIndex());
                            try
                            {
                                frame.setXOffset(Integer.parseInt(offsetX.getText()));
                            } catch (NumberFormatException e1)
                            {
                                // If the X offset isn't a number, simply don't perform the update.
                            }
                        }
                    }
                });
        offsetY.addFocusListener(
                new FocusAdapter()
                {
                    public void focusLost(FocusEvent e)
                    {
                        if ((core.getAnimation() != null) &&
                            (directionList.getSelectedIndex() != -1) &&
                            (frameList.getSelectedIndex() != -1))
                        {
                            AnimationFrame frame = core.getAnimation().getFrame(
                                    directionList.getSelectedIndex(),
                                    frameList.getSelectedIndex());
                            try
                            {
                                frame.setYOffset(Integer.parseInt(offsetY.getText()));
                            } catch (NumberFormatException nfe)
                            {
                                JOptionPane.showMessageDialog(
                                        scopedThis, "The contents of the Y-Offset field must be an integer.",
                                        "Not An Integer", JOptionPane.ERROR_MESSAGE);
                                offsetY.setText(Integer.toString(frame.getYOffset()));
                                offsetY.requestFocus();
                            }
                        }
                    }
                });
        offsetY.getDocument().addDocumentListener(
                new DocumentListener()
                {
                    public void removeUpdate(DocumentEvent e)
                    {
                        performUpdate();
                    }

                    public void insertUpdate(DocumentEvent e)
                    {
                        performUpdate();
                    }

                    public void changedUpdate(DocumentEvent e)
                    {
                        performUpdate();
                    }

                    private void performUpdate()
                    {
                        if ((core.getAnimation() != null) &&
                            (directionList.getSelectedIndex() != -1) &&
                            (frameList.getSelectedIndex() != -1))
                        {
                            AnimationFrame frame = core.getAnimation().getFrame(
                                    directionList.getSelectedIndex(),
                                    frameList.getSelectedIndex());
                            try
                            {
                                frame.setYOffset(Integer.parseInt(offsetY.getText()));
                            } catch (NumberFormatException e1)
                            {
                                // If the Y offset isn't a number, simply don't perform the update.
                            }
                        }
                    }
                });

        // Enable drag-and-drop loading behavior for files
        DropTarget dt = new DropTarget(
                imageComponent,
                new DropTargetAdapter()
                {
                    public void drop(DropTargetDropEvent dtde)
                    {
                        for (DataFlavor flavor : dtde.getCurrentDataFlavors())
                        {
                            if (flavor.isFlavorJavaFileListType())
                            {
                                dtde.acceptDrop(dtde.getDropAction());
                                List list = null;
                                try
                                {
                                    list = (List) (dtde.getTransferable().getTransferData(flavor));
                                } catch (UnsupportedFlavorException e)
                                {
                                    // If the file list data flavor isn't supported, we can't handle Drag & Drop of
                                    // files.  Bail out.
                                } catch (IOException e)
                                {
                                    // If the file list is somehow no longer available, we don't have necessary
                                    // information for the Drag & Drop import.  Bail out.
                                }
                                if (list != null)
                                {
                                    for (Object o : list)
                                    {
                                        if (o instanceof File)
                                        {
                                            if (configuration.getValue(CONFIG_ELEMENT_UI_CLEAR_ON_DND_IMPORTS))
                                            {
                                                performClear();
                                            }
                                            if (performLoad((File) o, false))
                                            {
                                                setActiveFile((File) o);
                                            } else
                                            {
                                                setActiveFile(null);
                                                performClear();
                                            }
                                        }
                                    }
                                }
                                return;
                            }
                        }
                    }
                });
        dt.isActive(); // To prevent any code analysis programs from thinking the DropTarget is unused.

        // Show warnings when button is pressed
        showWarningsButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        StringBuffer sb = new StringBuffer();
                        for (String warning : core.getAnimation().getWarnings())
                        {
                            sb.append("* ");
                            sb.append(warning);
                            sb.append('\n');
                        }
                        sb.delete(sb.length() - 1, sb.length());

                        final String errors = sb.toString();
                        new Thread("Animation Warnings Display")
                        {
                            public void run()
                            {
                                synchronized (showWarningsButton)
                                {
                                    ScrollableTextDialog.displayMessage(
                                            scopedThis,
                                            "Animation Warnings",
                                            "The following warnings exist for your animation file:",
                                            errors);
                                }
                            }
                        }.start();
                    }
                });

        extendedMenuBar.doLayout();
        updateDisplay();
        setSaveEnabled(false);

        // Pack
        this.pack();
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Sets the content pane of {@link SixDice} according to the provided integer.
     *
     * @param layout The layout (<b>not</b> {@link LayoutManager}) to use.
     */
    private void setContentPane(int layout)
    {
        String borderLayoutOrientation;
        if ((layout & 0x01) == 0)
        {
            borderLayoutOrientation = BorderLayout.EAST;
        } else
        {
            borderLayoutOrientation = BorderLayout.WEST;
        }

        imageScrollPane = new JScrollPane(imageComponent);
        ComponentConstructorPanel imageDisplayPane = new ComponentConstructorPanel(
                new BorderLayout(2, 2),
                new Pair<JComponent, Object>(imageScrollPane, BorderLayout.CENTER),
                new Pair<JComponent, Object>(
                        new ComponentConstructorPanel(
                                new SpongyLayout(SpongyLayout.Orientation.VERTICAL, false, false),
                                new ComponentConstructorPanel(
                                        new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL, true, false),
                                        new JLabel("Zoom: "),
                                        zoomField,
                                        new JLabel("%")),
                                zoomSlider),
                        BorderLayout.SOUTH));
        JPanel controlWidgetPanel =
                new ComponentConstructorPanel(
                        new SpongyLayout(SpongyLayout.Orientation.VERTICAL),
                        new ComponentConstructorPanel(
                                new InformalGridLayout(
                                        2, 4, 5, 5,
                                        false),
                                new SelfReturningJLabel("Width:").setAlignmentXAndReturn(Component.RIGHT_ALIGNMENT),
                                labelWidth,
                                new SelfReturningJLabel("Height:").setAlignmentXAndReturn(Component.RIGHT_ALIGNMENT),
                                labelHeight,
                                new SelfReturningJLabel("X Offset:").setAlignmentXAndReturn(Component.RIGHT_ALIGNMENT),
                                offsetX,
                                new SelfReturningJLabel("Y Offset:").setAlignmentXAndReturn(Component.RIGHT_ALIGNMENT),
                                offsetY),
                        new ComponentConstructorPanel(
                                new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL, false, false),
                                warningsPanel),
                        new ComponentConstructorPanel(
                                new SpongyLayout(SpongyLayout.Orientation.VERTICAL, false, false),
                                new SelfReturningJLabel("Palette:"),
                                paletteBox,
                                paletteRendering));
        JPanel directionPanel =
                new ComponentConstructorPanel(
                        new BorderLayout(),
                        new Pair<JComponent, Object>(
                                new SelfReturningJLabel("Directions:").setFontAndReturn(
                                        new Font("dialog", Font.BOLD, 14)),
                                BorderLayout.NORTH),
                        new Pair<JComponent, Object>(
                                new SizeConstructorScrollPane(directionList, 75, 150),
                                BorderLayout.CENTER),
                        new Pair<JComponent, Object>(
                                new ComponentConstructorPanel(
                                        new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL),
                                        directionAddButton,
                                        directionInsertButton,
                                        directionRemoveButton), BorderLayout.SOUTH));
        JPanel framePanel =
                new ComponentConstructorPanel(
                        new BorderLayout(),
                        new Pair<JComponent, Object>(
                                new SelfReturningJLabel("Frames:").setFontAndReturn(new Font("dialog", Font.BOLD, 14)),
                                BorderLayout.NORTH),
                        new Pair<JComponent, Object>(
                                new SizeConstructorScrollPane(
                                        frameList,
                                        75, 150),
                                BorderLayout.CENTER),
                        new Pair<JComponent, Object>(
                                new ComponentConstructorPanel(
                                        new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL),
                                        frameAddButton,
                                        frameInsertButton,
                                        frameRemoveButton), BorderLayout.SOUTH));

        if ((layout & 0x02) == 0)
        {
            this.setContentPane(
                    new ComponentConstructorPanel(
                            new BorderLayout(),
                            new Pair<JComponent, Object>(imageDisplayPane, BorderLayout.CENTER),
                            new Pair<JComponent, Object>(
                                    new ComponentConstructorPanel(
                                            new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL),
                                            controlWidgetPanel,
                                            directionPanel,
                                            framePanel), borderLayoutOrientation)));
        } else
        {
            this.setContentPane(
                    new ComponentConstructorPanel(
                            new BorderLayout(),
                            new Pair<JComponent, Object>(
                                    new ComponentConstructorPanel(
                                            new BorderLayout(),
                                            new Pair<JComponent, Object>(imageDisplayPane, BorderLayout.CENTER),
                                            new Pair<JComponent, Object>(
                                                    new ComponentConstructorPanel(
                                                            new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL),
                                                            controlWidgetPanel),
                                                    borderLayoutOrientation)),
                                    BorderLayout.CENTER),
                            new Pair<JComponent, Object>(
                                    new ComponentConstructorPanel(
                                            new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL),
                                            directionPanel,
                                            framePanel), BorderLayout.SOUTH)));
        }
        pack();
    }

    /**
     * This method removes the specified direction from the {@link Animation} currently loaded in memory as well as from
     * the various GUI widgets.  If no animation is loaded or no direction selected (the provided value is
     * <code>-1</code>), an error message is displayed instead.
     *
     * @param directions The directions to remove, or <code>-1</code> or an empty array if no direction is selected.
     */
    protected void performDirectionRemove(int... directions)
    {
        if (core.getAnimation() == null)
        {
            JOptionPane.showMessageDialog(
                    this, "No animation currently loaded.", "No Animation Loaded", JOptionPane.ERROR_MESSAGE);
        } else
        {
            if ((directions.length == 0) || (directions[0] == -1))
            {
                JOptionPane.showMessageDialog(
                        this, "You must select a direction to remove.", "No Direction Selected",
                        JOptionPane.ERROR_MESSAGE);
            } else
            {
                boolean confirm = false;
                for (int j : directions)
                {
                    for (int i = 0; i < core.getAnimation().getFrameCount(); i++)
                    {
                        BufferedImage image = core.getAnimation().getFrame(j, i).getImage();
                        if ((image.getWidth() > 1) || (image.getHeight() > 1))
                        {
                            confirm = true;
                            break;
                        }
                    }
                }
                if ((!confirm) ||
                    (JOptionPane.showConfirmDialog(
                            this,
                            "<html>Are you sure you want to delete all of the images in that " +
                            "direction?<br>This operation cannot be undone.",
                            "Are You Sure?",
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION))
                {
                    for (int i = directions.length - 1; i >= 0; i--)
                    {
                        core.getAnimation().removeDirection(directions[i]);
                    }
                    ((IntegerRangeListModel) (directionList.getModel())).setMaximum(
                            core.getAnimation().getDirectionCount());
                    directionList.setSelectedIndices(Utilities.EMPTY_INT_ARRAY);
                    contentsUpdated();
                    updateDisplay();
                }
            }
        }
    }

    /**
     * Retrieves the file types which are supported by this editor.
     *
     * @return The {@link FileType}s for this editor.
     */
    public FileType[] getFileTypes()
    {
        List<FileType> ret = new ArrayList<FileType>();
        for (AnimationCodec codec : codecs)
        {
            ret.add(codec.getFileType());
        }
        return ret.toArray(FileType.EMPTY_FILE_TYPE_ARRAY);
    }

    /**
     * Loads a file into the editor.  Assumes that the user will be prompted for imports.
     *
     * @param file The file to be loaded.
     * @return <code>true</code> if the load was successful; <code>false</code> otherwise.
     */
    public boolean performLoad(File file)
    {
        return performLoad(file, true);
    }

    /**
     * Loads a file into the editor.
     *
     * @param file            The file to be loaded.
     * @param promptForImport If the file isn't an animation file, this method will attempt to import the file as an
     *                        image and create a single-frame animation.  If this parameter is <code>true</code>, the
     *                        user will be prompted to say whether or not this is okay; if this parameter is
     *                        <code>false</code>, the import will occur without confirmation.
     * @return <code>true</code> if the load was successful; <code>false</code> otherwise.
     */
    public boolean performLoad(File file, boolean promptForImport)
    {
        try
        {
            ProgressTracker tracker = new ProgressBarTracker(waitingDialogProgressBar);
            waitingDialogDisplayer.setVisible(true);
            core.loadAnimation(file, getPalette(), tracker);
            waitingDialogDisplayer.setVisible(false);
            if (core.getAnimation() == null)
            {
                if ((!promptForImport) || (JOptionPane.showConfirmDialog(
                        this,
                        "<html>" + file +
                        " does not appear to be a valid animation file.<br>Would you like to try to import that image now?",
                        "Could Not Load Animation",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE) ==
                                                     JOptionPane.YES_OPTION))
                {
                    return performImport(file);
                } else
                {
                    performClear();
                    JOptionPane.showMessageDialog(
                            this, "File not loaded.", "Could Not Load File",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        } catch (IOException e)
        {
            waitingDialogDisplayer.setVisible(false);
            performClear();
            JOptionPane.showMessageDialog(
                    this, "<html>The following error occurred when trying to load " + file +
                          ":<br>" +
                          e.getMessage(), "Could Not Load Animation", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally
        {
            waitingDialogDisplayer.setVisible(false);
        }
        resetListSelection();
        updateDisplay();
        return true;
    }

    /**
     * Creates a new file within this editing frame.
     */
    public void performNew()
    {
        core.createNewAnimation();
        resetListSelection();
        contentsUpdated();
        updateDisplay();
    }

    /**
     * Clears the current animation, replacing it with the splash screen.
     */
    public void performClear()
    {
        core.clearAnimation();
        resetListSelection();
        contentsUpdated();
        updateDisplay();
    }

    /**
     * Saves the current editor's file.
     *
     * @param file The file into which the current data is to be saved.
     * @return <code>true</code> if the save was successful; <code>false</code> otherwise.
     */
    public boolean performSave(final File file)
    {
        if (core.getAnimation() == null) return true;
        List<Pair<String, AnimationCodec.MessageType>> messages = core.checkAnimation(file);
        boolean doSave = true;
        if (messages.size() > 0)
        {
            StringBuffer sb = new StringBuffer();
            boolean fatal = false;
            for (Pair<String, AnimationCodec.MessageType> message : messages)
            {
                if (sb.length() > 0) sb.append('\n');
                if (message.getSecond().equals(AnimationCodec.MessageType.FATAL))
                {
                    fatal = true;
                    sb.append("FATAL: ");
                }
                sb.append(message.getFirst());
            }
            if (fatal)
            {
                doSave = false;
                ScrollableTextDialog.displayMessage(
                        this, "Cannot Save", "Your animation cannot be saved in the specified format.",
                        sb.toString());
            } else
            {
                if (JOptionPane.showConfirmDialog(
                        this, "<html>Your animation raised the following warnings:<ul><li>" +
                              sb.toString().replaceAll("\n", "<li>") +
                              "</ul>Would you like to save anyway?",
                        "Warnings", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
                {
                    doSave = false;
                }
            }
        }
        if (doSave)
        {
            boolean ret = false;
            ProgressTracker tracker = new ProgressBarTracker(waitingDialogProgressBar);
            waitingDialogDisplayer.setVisible(true);
            try
            {
                try
                {
                    core.saveAnimation(file, getPalette(), tracker);
                    ret = true;
                } catch (IOException ioe)
                {
                    waitingDialogDisplayer.setVisible(false);
                    JOptionPane.showMessageDialog(
                            this,
                            "<html>The following error occurred while trying to save " + file + ":<br>" +
                            ioe.getMessage(),
                            "Could Not Save Animation", JOptionPane.ERROR_MESSAGE);
                }
            } finally
            {
                waitingDialogDisplayer.setVisible(false);
            }

            if ((ret) && (configuration.getValue(CONFIG_ELEMENT_UI_SAVED_FILES_RELOADED)))
            {
                performLoad(file);
            }

            return ret;
        } else
        {
            return false;
        }
    }

    /**
     * Allows the user to import a non-animation graphics document supported by the ImageIO framework.
     */
    public void performMenuImport()
    {
        String selectedFilterName = configuration.getValue(CONFIG_ELEMENT_PORT_FILTER);
        JZSwingUtilities.setJFileChooserFilters(
                fileChooser,
                (selectedFilterName == null) ?
                JZSwingUtilities.getAllImageReadersFileFilter() :
                JZSwingUtilities.getImageReaderFileFilterFor(selectedFilterName),
                true,
                JZSwingUtilities.getImageLoadingFileFilters());
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            FileFilter filter = fileChooser.getFileFilter();
            if ((filter instanceof SwingFileFilterWrapper) &&
                (((SwingFileFilterWrapper) filter).getFilter() instanceof FileExtensionFilter))
            {
                FileExtensionFilter fef = (FileExtensionFilter) (((SwingFileFilterWrapper) filter).getFilter());
                selectedFilterName = fef.getExtensions()[0];
                configuration.setValue(CONFIG_ELEMENT_PORT_FILTER, selectedFilterName);
            } else
            {
                configuration.revertToDefault(CONFIG_ELEMENT_PORT_FILTER);
            }
            File importFile = fileChooser.getSelectedFile();
            if ((selectedFilterName != null) && (!importFile.exists()))
            {
                importFile = FileUtilities.coerceFileExtension(importFile, '.' + selectedFilterName);
            }

            performImport(importFile);
        }
    }

    /**
     * Performs an import of the specified file.
     *
     * @param file The file to import.
     * @return <code>true</code> if the import was successful; <code>false</code> otherwise.
     */
    public boolean performImport(File file)
    {
        waitingDialogDisplayer.setVisible(true);
        String error;
        try
        {
            if (core.getAnimation() == null)
            {
                error = core.importImage(file);
                resetListSelection();
            } else
            {
                error =
                        core.importImage(
                                file, directionList.getSelectedIndex(), frameList.getSelectedIndex(),
                                configuration.getValue(CONFIG_ELEMENT_IMPORT_INSERTS));
            }
        } finally
        {
            waitingDialogDisplayer.setVisible(false);
        }
        if (error != null)
        {
            JOptionPane.showMessageDialog(
                    this, "<html>" + error.replaceAll("\n", "<br>"), "Could Not Load Image", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        boolean performSplitAfterImport = false;
        BufferedImage image = core.getAnimation().getFrame(
                directionList.getSelectedIndex(), frameList.getSelectedIndex()).getImage();
        if ((image.getWidth() > 256) || (image.getHeight() > 256))
        {
            int widthInFrames = (int) (Math.ceil(image.getWidth() / 256.0));
            int heightInFrames = (int) (Math.ceil(image.getHeight() / 256.0));
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "<html>" +
                    AWTUtilities.getNewlinedString(
                            "The provided image is larger than 256x256, which is the maximum size Diablo II supports " +
                            "for DC6 frames.  If this file will be saved as a DC6, this image will have to be split " +
                            "over multiple frames.  If there are already frames after the currently-selected frame, " +
                            "they will be overwritten to make room for this process.  This operation will require " +
                            (widthInFrames * heightInFrames) +
                            " frames.  If there are not enough frames, " +
                            "they will be created.\n\nWould you like SixDice to split this image for you?",
                            new Font("dialog", Font.BOLD, 12), 600).replaceAll("\n", "<br>"),
                    "Multi-Frame Import Suggested",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION)
            {
                performSplitAfterImport = true;
            } else if (result != JOptionPane.NO_OPTION)
            {
                return false;
            }
        }

        if (performSplitAfterImport)
        {
            performMenuSplitFrame();
        }

        setSaveEnabled(true);
        contentsUpdated();
        updateDisplay();
        return true;
    }

    /**
     * This method is designed to allow a user to perform a series of imports based upon the separator in the
     * configuration file.  The imported files must be of the format filename__x__y (where "<code>__</code>" is the
     * separator and x and y are positive integers of arbitrary length).  This function will load the images from all of
     * those files using the current palette, treating X as the direction number and Y as the frame number.  These
     * numbers are counted from zero.
     */
    public void performMenuImportSeries()
    {
        String selectedFilterName = configuration.getValue(CONFIG_ELEMENT_PORT_FILTER);
        JZSwingUtilities.setJFileChooserFilters(
                fileChooser,
                (selectedFilterName == null) ?
                JZSwingUtilities.getAllImageReadersFileFilter() :
                JZSwingUtilities.getImageReaderFileFilterFor(selectedFilterName),
                true,
                JZSwingUtilities.getImageLoadingFileFilters());
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            FileFilter filter = fileChooser.getFileFilter();
            if ((filter instanceof SwingFileFilterWrapper) &&
                (((SwingFileFilterWrapper) filter).getFilter() instanceof FileExtensionFilter))
            {
                FileExtensionFilter fef = (FileExtensionFilter) (((SwingFileFilterWrapper) filter).getFilter());
                selectedFilterName = fef.getExtensions()[0];
                configuration.setValue(CONFIG_ELEMENT_PORT_FILTER, selectedFilterName);
            } else
            {
                configuration.revertToDefault(CONFIG_ELEMENT_PORT_FILTER);
            }
            File importFile = fileChooser.getSelectedFile();
            if ((selectedFilterName != null) && (!importFile.exists()))
            {
                importFile = FileUtilities.coerceFileExtension(importFile, '.' + selectedFilterName);
            }

            String separator = configuration.getValue(CONFIG_ELEMENT_PORT_SEPARATOR);
            String[] segments = FileUtilities.removeFileExtension(importFile).getName().split(separator);
            if ((segments.length != 1) &&
                (((segments.length != 3) ||
                  ((segments.length > 1) && ((!MathUtilities.isNumber(segments[1], 10))) ||
                   (Integer.parseInt(segments[1]) < 0)) ||
                                                        ((segments.length > 2) &&
                                                         ((!MathUtilities.isNumber(segments[2], 10))) ||
                                                                                                      (Integer.parseInt(
                                                                                                              segments[2]) <
                                                                                                                           0)))))
            {
                ScrollableTextDialog.displayMessage(
                        this,
                        "Import Failed",
                        "Your import selection was invalid.",
                        "Your import selection was invalid.  To import, you must select a file form a " +
                        "batch-importable series.  A batch importable series has the format:\n\n" +
                        "     filename" + separator + 'x' + separator + "y.ext\n\n" +
                        "where ext is the extension of the file, and X and Y are non-negative integers.  The X " +
                        "number is interpreted as a direction number and the Y number is interpreted as a frame " +
                        "number.  For example, the files \"flippy" + separator + '0' +
                        separator +
                        "0.gif\" through " +
                        "\"flippy" + separator + '0' + separator +
                        "16.gif\" would be imported as a animation with a single " +
                        "direction and 17 frames in that direction.\n\n" +
                        "The numbers can optionally have leading zeroes (i.e., \"flippy" +
                        separator +
                        '0' +
                        separator +
                        "00.gif\") without affecting the import.  The separator sequence (currently \"" +
                        separator +
                        "\") can be specified in this program's configuration.");
                return;
            }

            String extension = FileUtilities.getFileExtension(importFile);
            if (performImportSeries(
                    new File(
                            importFile.getParent() + File.separatorChar + segments[0] +
                            ((extension.length() > 0) ? '.' + extension : ""))))
            {
                resetListSelection();
                setSaveEnabled(true);
                updateDisplay();
            } else
            {
                performClear();
            }
        }
    }

    /**
     * Performs the import series operation.
     *
     * @param file The {@link File} used to generate the filenames for the import.
     * @return <code>true</code> if the import was successful; <code>false</code> otherwise.
     */
    protected boolean performImportSeries(File file)
    {
        String error = core.importSeries(file, configuration.getValue(CONFIG_ELEMENT_PORT_SEPARATOR));
        if (error != null)
        {
            JOptionPane.showMessageDialog(
                    this, "<html>" + error.replaceAll("\n", "<br>"),
                    "Import Series Failed", JOptionPane.ERROR_MESSAGE);
        }
        return (error == null);
    }

    /**
     * Allows the user to specify a location into which the current image should be saved.
     */
    public void performMenuExport()
    {
        String selectedFilterName = configuration.getValue(CONFIG_ELEMENT_PORT_FILTER);
        JZSwingUtilities.setJFileChooserFilters(
                fileChooser,
                (selectedFilterName == null) ?
                null :
                JZSwingUtilities.getImageWriterFileFilterFor(selectedFilterName),
                false,
                JZSwingUtilities.getImageSavingFileFilters());
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            FileFilter filter = fileChooser.getFileFilter();
            if ((filter instanceof SwingFileFilterWrapper) &&
                (((SwingFileFilterWrapper) filter).getFilter() instanceof FileExtensionFilter))
            {
                FileExtensionFilter fef = (FileExtensionFilter) (((SwingFileFilterWrapper) filter).getFilter());
                selectedFilterName = fef.getExtensions()[0];
                configuration.setValue(CONFIG_ELEMENT_PORT_FILTER, selectedFilterName);
            } else
            {
                configuration.revertToDefault(CONFIG_ELEMENT_PORT_FILTER);
            }
            File exportFile = fileChooser.getSelectedFile();
            if (selectedFilterName != null)
            {
                exportFile = FileUtilities.coerceFileExtension(exportFile, '.' + selectedFilterName);
            }
            performExport(
                    exportFile,
                    (selectedFilterName == null) ?
                    FileUtilities.getFileExtension(exportFile) :
                    selectedFilterName);
        }
    }

    /**
     * Exports the currently-displayed image from the animation to a file.
     *
     * @param file   The file to which the image should be written.
     * @param format The name of the format in which to write.
     * @return <code>true</code> if the export was successful; <code>false</code> otherwise.
     */
    public boolean performExport(File file, String format)
    {
        String error = core.exportImage(
                file, format, directionList.getSelectedIndex(), frameList.getSelectedIndex());
        if (error != null)
        {
            JOptionPane.showMessageDialog(
                    this, "<html>" +
                          StringUtilities.getHtmlSafeString(error).replaceAll("\n", "<br>"),
                    "Export Failed", JOptionPane.ERROR_MESSAGE);
            return false;
        } else
        {
            return true;
        }
    }

    /**
     * Allows the user to specify a location for an export series.  Each of the images in the animation will be written
     * to the disk in a file with a padded background equal to the transparency color.
     */
    public void performMenuExportSeries()
    {
        String selectedFilterName = configuration.getValue(CONFIG_ELEMENT_PORT_FILTER);
        JZSwingUtilities.setJFileChooserFilters(
                fileChooser,
                (selectedFilterName == null) ?
                JZSwingUtilities.getAllImageWritersFileFilter() :
                JZSwingUtilities.getImageWriterFileFilterFor(selectedFilterName),
                false,
                JZSwingUtilities.getImageSavingFileFilters());
        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        String[] extensions = StringUtilities.EMPTY_STRING_ARRAY;
        FileFilter filter = fileChooser.getFileFilter();
        if ((filter instanceof SwingFileFilterWrapper) &&
            (((SwingFileFilterWrapper) filter).getFilter() instanceof FileExtensionFilter))
        {
            FileExtensionFilter fef = (FileExtensionFilter) (((SwingFileFilterWrapper) filter).getFilter());
            extensions = fef.getExtensions();
            selectedFilterName = extensions[0];
            for (int i = 0; i < extensions.length; i++)
            {
                extensions[i] = '.' + extensions[i];
            }
            configuration.setValue(CONFIG_ELEMENT_PORT_FILTER, selectedFilterName);
        } else
        {
            configuration.revertToDefault(CONFIG_ELEMENT_PORT_FILTER);
        }

        // By this point, the user has provided a file as a foundation for the export series.  Validate it.
        File f = FileUtilities.coerceFileExtension(fileChooser.getSelectedFile(), extensions);
        String extension = FileUtilities.getFileExtension(f);
        f = FileUtilities.removeFileExtension(f);
        String separator = configuration.getValue(CONFIG_ELEMENT_PORT_SEPARATOR);
        boolean containsSeparator = f.getName().contains(separator);
        if (containsSeparator)
        {
            f = new File(f.getParent() + File.separatorChar + f.getName().split(separator)[0]);
        }
        f = new File(f.getPath() + '.' + extension);
        if (containsSeparator)
        {
            if (JOptionPane.showConfirmDialog(
                    this,
                    "<html>The filename you specified contains the import/export separator string.  It should " +
                    "not;<br> otherwise, SixDice will be unable to import the series.<br><br>" +
                    "Instead, you could use the filename:<br>    " + f + "<br>Is that okay?",
                    "Invalid Export Series Filename",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE)
                != JOptionPane.YES_OPTION)
            {
                return;
            }
        }

        // By this point, we have a valid foundation file.  Export.
        performExportSeries(f, selectedFilterName);
    }

    /**
     * Performs a series export using the current animation file.
     *
     * @param file               The name of the generator file for this export series.
     * @param selectedFilterName The name of the filter.  This will be used as the file's extension.
     */
    protected void performExportSeries(File file, String selectedFilterName)
    {
        String error = core.exportSeries(
                file, selectedFilterName,
                configuration.getValue(CONFIG_ELEMENT_PORT_SEPARATOR));
        if (error != null)
        {
            ScrollableTextDialog.displayMessage(
                    this,
                    "Errors During Series Export",
                    "<html>The following errors occurred during the series export:",
                    error.replaceAll("\n", "<html>"));
        }
    }

    /**
     * Allows the user to instruct {@link SixDice} to split a frame into multiple segments.
     */
    public void performMenuSplitFrame()
    {
        final JDialog dialog = new JDialog(this, "Split Frame", true);
        final InterpretedTextField<Integer> widthField = new InterpretedTextField<Integer>(
                new BoundedIntegerInterpreter(1, Integer.MAX_VALUE), 256, 4);
        final InterpretedTextField<Integer> heightField = new InterpretedTextField<Integer>(
                new BoundedIntegerInterpreter(1, Integer.MAX_VALUE), 256, 4);
        final JCheckBox insertBox = new JCheckBox("", configuration.getValue(CONFIG_ELEMENT_FRAME_SPLIT_INSERTS));
        final SixDiceFrame scopedThis = this;

        ApprovalButtonPanel buttonPanel = new ApprovalButtonPanel(true, false)
        {
            public boolean apply()
            {
                if (widthField.getValue() == null)
                {
                    JOptionPane.showMessageDialog(
                            scopedThis, "Width must be a positive integer.", "Invalid Width",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                if (heightField.getValue() == null)
                {
                    JOptionPane.showMessageDialog(
                            scopedThis, "Height must be a positive integer.", "Invalid Height",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }

                performSplitFrame(widthField.getValue(), heightField.getValue(), insertBox.isSelected());
                return true;
            }

            public void close()
            {
                dialog.setVisible(false);
                dialog.dispose();
            }
        };

        dialog.setContentPane(
                new ComponentConstructorPanel(
                        new SpongyLayout(SpongyLayout.Orientation.VERTICAL),
                        new Pair<JComponent, Object>(
                                new ComponentConstructorPanel(
                                        new InformalGridLayout(2, 3, 2, 2, true),
                                        new SelfReturningJLabel("Width: ").setAlignmentXAndReturn(
                                                Component.RIGHT_ALIGNMENT),
                                        widthField,
                                        new SelfReturningJLabel("Height: ").setAlignmentXAndReturn(
                                                Component.RIGHT_ALIGNMENT),
                                        heightField,
                                        new SelfReturningJLabel("Insert? ").setAlignmentXAndReturn(
                                                Component.RIGHT_ALIGNMENT),
                                        insertBox),
                                SpongyLayout.PRIORITY_NORMAL),
                        new Pair<JComponent, Object>(buttonPanel, SpongyLayout.PRIORITY_PREFERRED)));

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Splits the currently-selected frame using the specified dimensions.
     *
     * @param width  The maximum width for each new frames.
     * @param height The maximum height for each new frame.
     * @param insert <code>true</code> if the new frames are inserted; <code>false</code> if they replace existing
     *               frames.
     */
    protected void performSplitFrame(int width, int height, boolean insert)
    {
        if (core.getAnimation() != null)
        {
            core.splitFrame(
                    directionList.getSelectedIndex(), frameList.getSelectedIndex(),
                    width, height, insert);
            contentsUpdated();
            updateDisplay();
        }
    }

    /**
     * Allows the user to command a frame join.  This creates a new image by joining multiple frames; the dimensions are
     * predetermined but can be changed.  The new image can then be used to replace one of the frames or exported as a
     * normal image file.
     */
    public void performMenuJoinFrames()
    {
        // TODO: Move the join-specific functionality to SixDiceCore.
        // Specifically, SixDiceCore should probably get two new methods: one which creates the joined image (for
        // preview purposes, at the least) and one which actually replaces frames with the joined frame.

        if (core.getAnimation() == null)
        {
            JOptionPane.showMessageDialog(
                    this, "Cannot join: no animation loaded.", "No Animation Loaded",
                    JOptionPane.ERROR_MESSAGE);
        }
        final int[] indices = frameList.getSelectedIndices();
        if (indices.length < 2)
        {
            JOptionPane.showMessageDialog(
                    this, "Select at least two frames to join.", "Insufficient Frames",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Let's take a guess at what the grid should look like.
        final BufferedImage[] images = new BufferedImage[indices.length];
        int maxWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < indices.length; i++)
        {
            images[i] = core.getAnimation().getFrame(directionList.getSelectedIndex(), indices[i]).getImage();
            maxWidth = Math.max(maxWidth, images[i].getWidth());
            maxHeight = Math.max(maxHeight, images[i].getHeight());
        }

        int gridWidth;
        int gridHeight;

        int nextMaxWidth = 0;
        int nextMaxWidthIndex = -1;
        int nextMaxHeight = 0;
        int index = 0;
        for (BufferedImage bi : images)
        {
            if ((bi.getWidth() < maxWidth) && (nextMaxWidth < bi.getWidth()))
            {
                nextMaxWidth = Math.max(nextMaxWidth, bi.getWidth());
                nextMaxWidthIndex = index;
            }
            if (bi.getHeight() < maxHeight)
            {
                nextMaxHeight = Math.max(nextMaxHeight, bi.getHeight());
            }
            index++;
        }

        if (nextMaxWidthIndex == -1)
        {
            gridWidth = Math.max(1, (int) (Math.sqrt(images.length)));
        } else
        {
            gridWidth = nextMaxWidthIndex + 1;
        }
        gridHeight = (images.length + gridWidth - 1) / gridWidth;

        // That should be a reasonable estimate.
        final InterpretedTextField<Integer> widthField = new InterpretedTextField<Integer>(
                new BoundedIntegerInterpreter(1, images.length), gridWidth, 2);
        final InterpretedTextField<Integer> heightField = new InterpretedTextField<Integer>(
                new BoundedIntegerInterpreter(1, images.length), gridHeight, 2);
        final JScrollPane imageScrollPane = new JScrollPane();

        final BufferedImage[] joinedImage = new BufferedImage[1];
        final ActionListener imageRefreshActionListener = new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if ((widthField.getValue() == null) || (heightField.getValue() == null)) return;
                int gridWidth = widthField.getValue();
                int gridHeight = heightField.getValue();
                int width = 0;
                int height = 0;

                for (int y = 0; y < gridHeight; y++)
                {
                    int maxHeight = 0;
                    int widthTotal = 0;
                    for (int x = 0; x < gridWidth; x++)
                    {
                        int index = y * gridWidth + x;
                        if (index < images.length)
                        {
                            maxHeight = Math.max(maxHeight, images[index].getHeight(null));
                            widthTotal += images[index].getWidth();
                        }
                    }
                    width = Math.max(width, widthTotal);
                    height += maxHeight;
                }

                BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics g = buffer.getGraphics();
                g.setColor(configuration.getValue(CONFIG_ELEMENT_IMPORT_VIRTUAL_CLEAR_COLOR));
                g.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());

                int heightPos = 0;
                for (int y = 0; y < gridHeight; y++)
                {
                    int widthPos = 0;
                    int maxHeight = 0;
                    for (int x = 0; x < gridWidth; x++)
                    {
                        int index = y * gridWidth + x;
                        if (index < images.length)
                        {
                            g.drawImage(images[index], widthPos, heightPos, null);
                            maxHeight = Math.max(maxHeight, images[index].getHeight(null));
                            widthPos += images[index].getWidth();
                        }
                    }
                    heightPos += maxHeight;
                }

                joinedImage[0] = buffer;
                imageScrollPane.setViewportView(new JLabel(new ImageIcon(buffer)));
            }
        };

        imageRefreshActionListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_FIRST, "updateImage"));

        // Establish a nice size for the display of the image.  This should be based upon the current display context.
        Rectangle displayConfigurationBounds = this.getGraphicsConfiguration().getBounds();
        imageScrollPane.setPreferredSize(
                new Dimension(
                        (int) (Math.min(
                                displayConfigurationBounds.getWidth() * 0.9, joinedImage[0].getWidth() + 20)),
                        (int) (Math.min(
                                displayConfigurationBounds.getHeight() * 0.9, joinedImage[0].getHeight() + 20))));

        // Construct the rest of the dialog.
        JButton refreshButton = new JButton("Refresh");
        refreshButton.setMnemonic(KeyEvent.VK_R);
        JButton replaceButton = new JButton("Replace");
        replaceButton.setMnemonic(KeyEvent.VK_P);
        JButton exportButton = new JButton("Export");
        exportButton.setMnemonic(KeyEvent.VK_E);
        JButton closeButton = new JButton("Close");
        closeButton.setMnemonic(KeyEvent.VK_C);

        final JDialog joinDialog = new JDialog(this, "Join Frames", true);
        joinDialog.setContentPane(
                new ComponentConstructorPanel(
                        new BorderLayout(),
                        new Pair<JComponent, Object>(imageScrollPane, BorderLayout.CENTER),
                        new Pair<JComponent, Object>(
                                new ComponentConstructorPanel(
                                        new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL),
                                        new JLabel("Grid Width: "),
                                        widthField,
                                        new JLabel("Grid Height: "),
                                        heightField,
                                        new ComponentConstructorPanel(
                                                new GridLayout(1, 4),
                                                refreshButton,
                                                replaceButton,
                                                exportButton,
                                                closeButton)),
                                BorderLayout.SOUTH)));

        // add button listeners
        final SixDiceFrame scopedThis = this;
        DocumentListener imageRefreshDocumentListener = new DocumentListener()
        {
            public void changedUpdate(DocumentEvent e)
            {
                imageRefreshActionListener.actionPerformed(
                        new ActionEvent(e.getDocument(), ActionEvent.ACTION_FIRST, "updateImage"));
            }

            public void insertUpdate(DocumentEvent e)
            {
                imageRefreshActionListener.actionPerformed(
                        new ActionEvent(e.getDocument(), ActionEvent.ACTION_FIRST, "updateImage"));
            }

            public void removeUpdate(DocumentEvent e)
            {
                imageRefreshActionListener.actionPerformed(
                        new ActionEvent(e.getDocument(), ActionEvent.ACTION_FIRST, "updateImage"));
            }
        };

        widthField.getDocument().addDocumentListener(imageRefreshDocumentListener);
        heightField.getDocument().addDocumentListener(imageRefreshDocumentListener);
        refreshButton.addActionListener(imageRefreshActionListener);
        replaceButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        for (int i = 1; i < indices.length; i++)
                        {
                            core.getAnimation().setFrame(
                                    directionList.getSelectedIndex(), indices[i], new AnimationFrame());
                        }
                        core.getAnimation().getFrame(directionList.getSelectedIndex(), indices[0]).setImage(
                                joinedImage[0]);
                        joinDialog.setVisible(false);
                        contentsUpdated();
                        updateDisplay();
                    }
                });
        exportButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        JZSwingUtilities.setJFileChooserFilters(
                                fileChooser, JZSwingUtilities.getImageWriterFileFilterFor(
                                configuration.getValue(CONFIG_ELEMENT_PORT_FILTER)),
                                false, JZSwingUtilities.getImageSavingFileFilters());
                        if (fileChooser.showSaveDialog(scopedThis) == JFileChooser.APPROVE_OPTION)
                        {
                            try
                            {
                                //noinspection SuspiciousMethodCalls
                                String format = JZSwingUtilities.getImageSavingFileFilterMap().get(
                                        fileChooser.getFileFilter());
                                File file =
                                        FileUtilities.coerceFileExtension(fileChooser.getSelectedFile(), '.' + format);
                                ImageIO.write(core.preProcessSavingImage(joinedImage[0]), format, file);
                                configuration.setValue(CONFIG_ELEMENT_PORT_FILTER, format);
                                joinDialog.setVisible(false);
                            } catch (IOException ioe)
                            {
                                JOptionPane.showMessageDialog(
                                        scopedThis, "<html>The following error occurred while attempting the " +
                                                    "export:<br>" + ioe.getMessage());
                            }
                        }
                    }
                });
        closeButton.addActionListener(
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        joinDialog.setVisible(false);
                    }
                });

        // pack and display
        joinDialog.pack();
        joinDialog.setLocationRelativeTo(this);
        joinDialog.setVisible(true);
        joinDialog.dispose();
    }

    /**
     * Performs a color changing operation on the {@link Animation} currently loaded into memory.
     */
    public void performMenuColorChange()
    {
        final JDialog dialog = new JDialog(this, "Color Change", true);
        Color[] colors = new Color[core.getAnimation().getDirectionCount() * core.getAnimation().getFrameCount()];
        for (int d = 0; d < core.getAnimation().getDirectionCount(); d++)
        {
            for (int f = 0; f < core.getAnimation().getFrameCount(); f++)
            {
                colors[d * core.getAnimation().getFrameCount() + f] = ImageUtilities
                        .getAverageColorIn(core.getAnimation().getFrame(d, f).getImage());
            }
        }
        final Color oldColor = AWTUtilities.blendColors(colors);
        final ColoredBlockIcon newColorIcon = new ColoredBlockIcon(30, 15, oldColor);
        final JLabel previewLabel = new JLabel(
                new ImageIcon(
                        ImageUtilities.copyImage(
                                core.getAnimation().getFrame(
                                        directionList.getSelectedIndex(),
                                        frameList.getSelectedIndex()).getImage())));
        final JSlider hueSlider = new JSlider(-128, 128, 0);
        hueSlider.setMajorTickSpacing(8);
        hueSlider.setMinorTickSpacing(1);
        hueSlider.setSnapToTicks(true);
        final JSlider saturationSlider = new JSlider(-128, 128, 0);
        saturationSlider.setMajorTickSpacing(8);
        saturationSlider.setMinorTickSpacing(1);
        saturationSlider.setSnapToTicks(true);
        final JSlider brightnessSlider = new JSlider(-128, 128, 0);
        brightnessSlider.setMajorTickSpacing(8);
        brightnessSlider.setMinorTickSpacing(1);
        brightnessSlider.setSnapToTicks(true);
        ApprovalButtonPanel abp = new ApprovalButtonPanel(true, false)
        {
            public boolean apply()
            {
                for (int d = 0; d < core.getAnimation().getDirectionCount(); d++)
                {
                    for (int f = 0; f < core.getAnimation().getFrameCount(); f++)
                    {
                        AnimationFrame frame = core.getAnimation().getFrame(d, f);
                        frame.setImage(
                                ImageUtilities.adjustHSB(
                                        frame.getImage(),
                                        hueSlider.getValue() / 128.0,
                                        saturationSlider.getValue() / 128.0,
                                        brightnessSlider.getValue() / 128.0));
                    }
                }
                contentsUpdated();
                updateDisplay();
                return true;
            }

            public void close()
            {
                dialog.setVisible(false);
            }
        };

        ChangeListener displayUpdateListener = new ChangeListener()
        {
            public void stateChanged(ChangeEvent e)
            {
                newColorIcon.setColor(
                        new Color(
                                AWTUtilities.adjustHSB(
                                        oldColor.getRGB(),
                                        hueSlider.getValue() / 128.0,
                                        saturationSlider.getValue() / 128.0,
                                        brightnessSlider.getValue() / 128.0)));
                previewLabel.setIcon(
                        new ImageIcon(
                                ImageUtilities.adjustHSB(
                                        ImageUtilities.copyImage(
                                                core.getAnimation().getFrame(
                                                        directionList.getSelectedIndex(),
                                                        frameList.getSelectedIndex()).getImage()),
                                        hueSlider.getValue() / 128.0,
                                        saturationSlider.getValue() / 128.0,
                                        brightnessSlider.getValue() / 128.0)));
                dialog.repaint();
            }
        };
        hueSlider.addChangeListener(displayUpdateListener);
        saturationSlider.addChangeListener(displayUpdateListener);
        brightnessSlider.addChangeListener(displayUpdateListener);

        dialog.setContentPane(
                new ComponentConstructorPanel(
                        new SpongyLayout(SpongyLayout.Orientation.VERTICAL),
                        new ComponentConstructorPanel(
                                new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL),
                                new JLabel("Old Average Color: "),
                                new JLabel(new ColoredBlockIcon(30, 15, oldColor)),
                                new SpacingComponent(10, 10),
                                new JLabel("New Average Color: "),
                                new JLabel(newColorIcon)),
                        new ComponentConstructorPanel(
                                new SpongyLayout(SpongyLayout.Orientation.HORIZONTAL),
                                new JLabel("Preview: "),
                                previewLabel),
                        new ComponentConstructorPanel(
                                new InformalGridLayout(2, 3, 2, 2, false),
                                new JLabel("Hue: "),
                                hueSlider,
                                new JLabel("Saturation: "),
                                saturationSlider,
                                new JLabel("Brightness: "),
                                brightnessSlider),
                        abp));

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        dialog.dispose();
    }

    /**
     * Allows the user to specify through a dialog a resize request on the images in the currently loaded {@link
     * Animation}.
     */
    public void performMenuResize()
    {
        final JDialog resizeDialog = new JDialog(this, "Resize", true);
        final JTextField scaleField = new JTextField("100", 3);
        final JCheckBox adjustOffsets = new JCheckBox("", true);
        final JComboBox scalingMethod = new JComboBox(new Object[]{"Replicate", "Smooth"});
        final int[] scalingMethodConstants = new int[]{Image.SCALE_REPLICATE, Image.SCALE_SMOOTH};
        ApprovalButtonPanel buttonPanel = new ApprovalButtonPanel(true, false)
        {
            public boolean apply()
            {
                int scaleValue;
                try
                {
                    scaleValue = Integer.parseInt(scaleField.getText());
                } catch (NumberFormatException e)
                {
                    JOptionPane.showMessageDialog(
                            resizeDialog, "Scale must be a positive integer.", "Invalid Scale",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                if ((scaleValue < 0) || (scaleValue > 10000))
                {
                    JOptionPane.showMessageDialog(
                            resizeDialog, "Scale must be between 1% and 10,000%.", "Invalid Scale",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                core.getAnimation().scale(
                        scaleValue / 100.0, adjustOffsets.isSelected(),
                        scalingMethodConstants[scalingMethod.getSelectedIndex()]);
                contentsUpdated();
                updateDisplay();
                return true;
            }

            public void close()
            {
                resizeDialog.dispose();
            }
        };
        resizeDialog.setContentPane(
                new ComponentConstructorPanel(
                        new BorderLayout(),
                        new Pair<JComponent, Object>(
                                new ComponentConstructorPanel(
                                        new InformalGridLayout(2, 3, 2, 2),
                                        new JLabel("Scale Percentage: "),
                                        scaleField,
                                        new JLabel("Adjust Offsets? "),
                                        adjustOffsets,
                                        new JLabel("Scaling Method: "),
                                        scalingMethod),
                                BorderLayout.CENTER),
                        new Pair<JComponent, Object>(buttonPanel, BorderLayout.SOUTH)));
        resizeDialog.pack();
        resizeDialog.setLocationRelativeTo(this);
        resizeDialog.setVisible(true);
    }

    /**
     * Allows the user to specify settings for a batch conversion through a dialog.
     */
    public void performMenuBatchConvert()
    {
        final JDialog batchConvertDialog = new JDialog(this, "Batch Conversion", true);
        batchConvertDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        final DirectoryPathField dpf = new DirectoryPathField(
                this, configuration.getValue(CONFIG_ELEMENT_BATCH_CONVERSION_PATH), 20);

        SwingFileFilterWrapper[] loadingFilters = JZSwingUtilities.getImageLoadingFileFilters();
        Object[] loadingOptions = new Object[codecs.length + loadingFilters.length];
        System.arraycopy(codecs, 0, loadingOptions, 0, codecs.length);
        System.arraycopy(
                loadingFilters, 0, loadingOptions, codecs.length,
                loadingOptions.length - codecs.length);

        SwingFileFilterWrapper[] savingFilter = JZSwingUtilities.getImageSavingFileFilters();
        Object[] savingOptions = new Object[codecs.length + savingFilter.length];
        System.arraycopy(codecs, 0, savingOptions, 0, codecs.length);
        System.arraycopy(savingFilter, 0, savingOptions, codecs.length, savingOptions.length - codecs.length);

        final JComboBox sourceType = new JComboBox(loadingOptions);
        final JComboBox targetType = new JComboBox(savingOptions);
        sourceType.setSelectedIndex(
                MathUtilities.bound(
                        configuration.getValue(CONFIG_ELEMENT_BATCH_CONVERSION_SOURCE_INDEX),
                        0, loadingOptions.length - 1));
        targetType.setSelectedIndex(
                MathUtilities.bound(
                        configuration.getValue(CONFIG_ELEMENT_BATCH_CONVERSION_TARGET_INDEX),
                        0, savingOptions.length - 1));

        final JCheckBox recursiveBox = new JCheckBox("Recursive");
        recursiveBox.setSelected(configuration.getValue(CONFIG_ELEMENT_BATCH_CONVERSION_RECURSIVE));

        final JComboBox paletteBox = new JComboBox(
                new SortedListModel(
                        Diablo2DefaultPalettes.PALETTE_MAP.keySet().toArray(StringUtilities.EMPTY_STRING_ARRAY), true));
        String selectedPalette = configuration.getValue(CONFIG_ELEMENT_BATCH_CONVERSION_PALETTE);
        if (selectedPalette == null)
        {
            paletteBox.setSelectedIndex(0);
        } else
        {
            paletteBox.setSelectedItem(selectedPalette);
        }

        final SixDiceFrame scopedThis = this;
        ApprovalButtonPanel buttonPanel = new ApprovalButtonPanel(true, false)
        {
            public boolean apply()
            {
                new Thread("Batch Convert Initiator")
                {
                    public void run()
                    {
                        final StreamPipe streamPipe = new StreamPipe();
                        final OutputStream outputStream = streamPipe.getOutputStream();
                        final File directory = new File(dpf.getPath());
                        final boolean recursive = recursiveBox.isSelected();

                        configuration.setValue(CONFIG_ELEMENT_BATCH_CONVERSION_PATH, directory.getPath());
                        configuration.setValue(CONFIG_ELEMENT_BATCH_CONVERSION_RECURSIVE, recursive);
                        configuration.setValue(
                                CONFIG_ELEMENT_BATCH_CONVERSION_SOURCE_INDEX, sourceType.getSelectedIndex());
                        configuration.setValue(
                                CONFIG_ELEMENT_BATCH_CONVERSION_TARGET_INDEX, targetType.getSelectedIndex());
                        //noinspection ObjectToString
                        configuration.setValue(
                                CONFIG_ELEMENT_BATCH_CONVERSION_PALETTE,
                                paletteBox.getSelectedItem().toString());

                        final JDialog logDialog = new JDialog(scopedThis, "Batch Conversion", true);
                        final JTextArea logArea = new JTextArea();
                        logArea.setEditable(false);
                        JScrollPane logAreaScrollPane = new JScrollPane(logArea);
                        logAreaScrollPane.setPreferredSize(new Dimension(400, 300));
                        final JButton closeButton = new JButton("Close");
                        closeButton.setEnabled(false);
                        logDialog.setContentPane(
                                new ComponentConstructorPanel(
                                        new BorderLayout(),
                                        new Pair<JComponent, Object>(
                                                logAreaScrollPane,
                                                BorderLayout.CENTER),
                                        new Pair<JComponent, Object>(
                                                closeButton,
                                                BorderLayout.SOUTH)));
                        logDialog.pack();
                        logDialog.setLocationRelativeTo(scopedThis);

                        closeButton.addActionListener(
                                new ActionListener()
                                {
                                    public void actionPerformed(ActionEvent e)
                                    {
                                        logDialog.setVisible(false);
                                    }
                                });

                        // Log update thread
                        new Thread("Log Update")
                        {
                            public void run()
                            {
                                FileOutputStream fos = null;
                                try
                                {
                                    int lineCount = 0;
                                    try
                                    {
                                        fos = new FileOutputStream(
                                                System.getProperty("user.home") + File.separatorChar +
                                                ".sixdice-batchconvert.log");
                                    } catch (FileNotFoundException e)
                                    {
                                        // We don't have to write a file copy of the log.
                                    }
                                    PrintStream ps = new PrintStream((fos == null) ? new NullOutputStream() : fos);
                                    BufferedReader br = new BufferedReader(
                                            new InputStreamReader(streamPipe.getInputStream()));
                                    boolean neverRead = true;
                                    String s = br.readLine();
                                    while (s != null)
                                    {
                                        ps.println(s);
                                        lineCount++;
                                        if (lineCount > 500)
                                        {
                                            String old = logArea.getText();
                                            int newlineIndex = old.indexOf("\n");
                                            logArea.setText(old.substring(newlineIndex + 1) + '\n' + s);
                                        } else
                                        {
                                            if (logArea.getText().length() > 0)
                                            {
                                                logArea.setText(logArea.getText() + '\n' + s);
                                            } else
                                            {
                                                logArea.setText(s);
                                            }
                                        }
                                        logArea.setText(logArea.getText().replaceAll(".\b", ""));
                                        neverRead = false;
                                        s = br.readLine();
                                    }
                                    if (neverRead)
                                    {
                                        logArea.setText("Nothing to do.");
                                        ps.println("Nothing to do.");
                                    }
                                    ps.close();
                                    if (fos != null)
                                    {
                                        fos.close();
                                    }
                                } catch (IOException ioe)
                                {
                                    logArea.setText("Internal logging error: " + ioe.getMessage());
                                }
                            }
                        }.start();

                        // Conversion thread
                        new Thread("Batch Converter")
                        {
                            public void run()
                            {
                                if (sourceType.getSelectedItem() instanceof AnimationCodec)
                                {
                                    if (targetType.getSelectedItem() instanceof AnimationCodec)
                                    {
                                        core.copy().batchConvert(
                                                directory, recursive,
                                                (AnimationCodec) (sourceType.getSelectedItem()),
                                                (AnimationCodec) (targetType.getSelectedItem()),
                                                Diablo2DefaultPalettes.PALETTE_MAP.get(paletteBox.getSelectedItem()),
                                                outputStream, outputStream, true);
                                    } else
                                    {
                                        core.copy().batchConvert(
                                                directory, recursive,
                                                (AnimationCodec) (sourceType.getSelectedItem()),
                                                ((FileExtensionFilter) (((SwingFileFilterWrapper) (targetType.getSelectedItem())).getFilter()))
                                                        .getExtensions()[0],
                                                configuration.getValue(CONFIG_ELEMENT_PORT_SEPARATOR),
                                                Diablo2DefaultPalettes.PALETTE_MAP.get(paletteBox.getSelectedItem()),
                                                outputStream, outputStream, true);
                                    }
                                } else
                                {
                                    if (targetType.getSelectedItem() instanceof AnimationCodec)
                                    {
                                        core.copy().batchConvert(
                                                directory, recursive,
                                                ((SwingFileFilterWrapper) (sourceType.getSelectedItem())).getFilter(),
                                                (AnimationCodec) (targetType.getSelectedItem()),
                                                configuration.getValue(CONFIG_ELEMENT_PORT_SEPARATOR),
                                                Diablo2DefaultPalettes.PALETTE_MAP.get(paletteBox.getSelectedItem()),
                                                outputStream, outputStream, true);
                                    } else
                                    {
                                        PrintStream ps = new PrintStream(outputStream);
                                        ps.println("Image-to-image conversion not supported.");
                                        ps.close();
                                    }
                                }

                                try
                                {
                                    streamPipe.getOutputStream().close();
                                } catch (IOException e1)
                                {
                                    // We can't close the pipe stream.  Not much else we can try.
                                }
                                closeButton.setEnabled(true);
                            }
                        }.start();

                        // show log dialog during conversion process
                        logDialog.addComponentListener(new DisposeOnHideListener());
                        // make sure that the stream closes
                        logDialog.addComponentListener(
                                new ComponentAdapter()
                                {
                                    public void componentHidden(ComponentEvent e)
                                    {
                                        try
                                        {
                                            streamPipe.getOutputStream().close();
                                        } catch (IOException e1)
                                        {
                                            // We can't close the pipe stream.  Not much else we can try.
                                        }
                                    }
                                });
                        logDialog.setVisible(true);
                    }
                }.start();
                return true;
            }

            public void close()
            {
                batchConvertDialog.setVisible(false);
                batchConvertDialog.dispose();
            }
        };

        ComponentConstructorPanel ccp = new ComponentConstructorPanel(
                new SpongyLayout(SpongyLayout.Orientation.VERTICAL, 5, 5),
                new SelfReturningJLabel("Please choose a directory containing the files to convert:"),
                dpf,
                recursiveBox,
                new ComponentConstructorPanel(
                        new BorderLayout(),
                        new Pair<JComponent, Object>(new JLabel("Palette: "), BorderLayout.WEST),
                        new Pair<JComponent, Object>(paletteBox, BorderLayout.CENTER)),
                new ComponentConstructorPanel(
                        new InformalGridLayout(1, 4, 2, 2),
                        new JLabel("Source: "), sourceType, new JLabel("Target: "), targetType),
                buttonPanel);
        batchConvertDialog.setContentPane(ccp);
        batchConvertDialog.pack();

        // Show dialog
        batchConvertDialog.setLocationRelativeTo(this);
        batchConvertDialog.setVisible(true);
    }


    /**
     * Sets whether or not the save options are enabled in the file menu.
     *
     * @param enabled <code>true</code> if the save menu items should be enabled; <code>false</code> otherwise.
     */
    protected void setSaveEnabled(boolean enabled)
    {
        super.setSaveEnabled(enabled);
        if (extendedMenuBar.getExtendedMenu("File").getItem("Export") != null)
        {
            extendedMenuBar.getExtendedMenu("File").getItem("Export").setEnabled(enabled);
            extendedMenuBar.getExtendedMenu("File").getItem("Export Series").setEnabled(enabled);

            extendedMenuBar.getExtendedMenu("Image").getItem("Trim Borders").setEnabled(enabled);
            extendedMenuBar.getExtendedMenu("Image").getItem("Adjust Offsets").setEnabled(enabled);
            extendedMenuBar.getExtendedMenu("Image").getItem("Center Offset").setEnabled(enabled);
            extendedMenuBar.getExtendedMenu("Image").getItem("Split Frame").setEnabled(enabled);
            extendedMenuBar.getExtendedMenu("Image").getItem("Join Frames").setEnabled(enabled);
            extendedMenuBar.getExtendedMenu("Image").getItem("Color Change").setEnabled(enabled);
            extendedMenuBar.getExtendedMenu("Image").getItem("Resize Images").setEnabled(enabled);

            directionAddButton.setEnabled(enabled);
            directionInsertButton.setEnabled(enabled);
            directionRemoveButton.setEnabled(enabled);
            frameAddButton.setEnabled(enabled);
            frameInsertButton.setEnabled(enabled);
            frameRemoveButton.setEnabled(enabled);
            zoomField.setEnabled(enabled);
            zoomSlider.setEnabled(enabled);
            if (!enabled) zoomField.setText("100");
        }
    }

    /**
     * Allows the user to instruct the animation to trim its borders.
     */
    public void performMenuTrimBorders()
    {
        if (core.getAnimation() == null)
        {
            JOptionPane.showMessageDialog(
                    this, "Cannot trim: no animation loaded.", "No Animation Loaded",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        core.getAnimation().trimBorders();
        updateDisplay();
    }

    /**
     * Allows the user to instruct the animation to adjust all of its offsets by a given value.
     */
    public void performMenuAdjustOffset()
    {
        if (core.getAnimation() == null)
        {
            JOptionPane.showMessageDialog(
                    this, "Cannot adjust offsets: no animation loaded.", "No Animation Loaded",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        final JDialog dialog = new JDialog(this, "Adjust Offsets", true);
        final JTextField xField = new JTextField("0", 3);
        final JTextField yField = new JTextField("0", 3);
        ApprovalButtonPanel buttonPanel = new ApprovalButtonPanel(true, false)
        {
            public boolean apply()
            {
                int x, y;
                try
                {
                    x = Integer.parseInt(xField.getText());
                } catch (NumberFormatException e)
                {
                    JOptionPane.showMessageDialog(
                            dialog, "X value is not a number.", "Invalid Number", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                try
                {
                    y = Integer.parseInt(yField.getText());
                } catch (NumberFormatException e)
                {
                    JOptionPane.showMessageDialog(
                            dialog, "Y value is not a number.", "Invalid Number", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                core.getAnimation().adjustOffsets(-x, -y);
                updateDisplay();
                return true;
            }

            public void close()
            {
                dialog.dispose();
            }
        };
        dialog.setContentPane(
                new ComponentConstructorPanel(
                        new SpongyLayout(SpongyLayout.Orientation.VERTICAL),
                        new ComponentConstructorPanel(
                                new InformalGridLayout(2, 2, 2, 2, false),
                                new JLabel("X: "),
                                xField,
                                new JLabel("Y: "),
                                yField),
                        buttonPanel));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Allows the user to instruct the animation to set all of its offsets relative to the center of the currently
     * selected image.
     */
    public void performMenuCenterOffset()
    {
        if (core.getAnimation() == null)
        {
            JOptionPane.showMessageDialog(
                    this, "Cannot center: no animation loaded.", "No Animation Loaded", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if ((directionList.getSelectedIndex() == -1) || (frameList.getSelectedIndex() == -1))
        {
            JOptionPane.showMessageDialog(
                    this, "Select a frame to act as the center-point.", "No Frame Selected.",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        core.centerOffsets(directionList.getSelectedIndex(), frameList.getSelectedIndex());
        updateDisplay();
    }

    /**
     * Allows the user to manipulate this program's general preferences.
     */
    public void performMenuPreferences()
    {
        final JDialog preferencesDialog = new JDialog(this, "Preferences", true);

        final JLabel layoutLabel = new JLabel("UI Layout Style: ");
        final JComboBox layoutComboBox = new JComboBox(
                new String[]{
                        "Wide-Left Layout", "Wide-Right Layout", "Tall-Left Layout", "Tall-Right Layout"});
        layoutComboBox.setSelectedIndex(configuration.getValue(CONFIG_ELEMENT_UI_LAYOUT));
        final JLabel backgroundColorLabel = new JLabel("Image Background Color: ");
        final ColorSelectorButton backgroundColorButton = new ColorSelectorButton(
                configuration.getValue(CONFIG_ELEMENT_UI_IMAGE_BACKGROUND), new Dimension(20, 12));
        final JLabel underlayColorLabel = new JLabel("Image Underlay Color: ");
        final ColorSelectorButton underlayColorButton = new ColorSelectorButton(
                configuration.getValue(CONFIG_ELEMENT_UI_IMAGE_UNDERLAY), new Dimension(20, 12));

        final JLabel dragAndDropImportClearLabel = new JLabel("Clear on Drag & Drop Imports? ");
        final JCheckBox dragAndDropImportClear = new JCheckBox(
                "", configuration.getValue(CONFIG_ELEMENT_UI_CLEAR_ON_DND_IMPORTS));
        final JLabel insertImportedFramesLabel = new JLabel("Insert Imported Frames? ");
        final JCheckBox insertImportedFrames = new JCheckBox(
                "", configuration.getValue(CONFIG_ELEMENT_IMPORT_INSERTS));
        final JLabel insertSplitFramesLabel = new JLabel("Insert Split Frames? ");
        final JCheckBox insertSplitFrames = new JCheckBox(
                "", configuration.getValue(CONFIG_ELEMENT_FRAME_SPLIT_INSERTS));
        final JLabel loadOnSaveLabel = new JLabel("Saved Files Reloaded? ");
        final JCheckBox loadOnSave = new JCheckBox(
                "", configuration.getValue(CONFIG_ELEMENT_UI_SAVED_FILES_RELOADED));

        final JLabel virtualClearColorEnabledLabel = new JLabel("Use Virtual Clear Color? ");
        final JCheckBox virtualClearColorEnabled = new JCheckBox(
                "", configuration.getValue(CONFIG_ELEMENT_IMPORT_VIRTUAL_CLEAR_COLOR) != null);
        final JLabel virtualClearColorLabel = new JLabel("Virtual Clear Color: ");
        final ColorSelectorButton virtualClearColorButton = new ColorSelectorButton(
                configuration.getValue(CONFIG_ELEMENT_IMPORT_VIRTUAL_CLEAR_COLOR), new Dimension(20, 12));
        final JLabel clearToVirtualOnExportLabel = new JLabel("Clear to Virtual on Export? ");
        final JCheckBox clearToVirtualOnExport = new JCheckBox(
                "", configuration.getValue(CONFIG_ELEMENT_EXPORT_CLEAR_TO_VIRTUAL));

        final JLabel multiFrameSeparatorLabel = new JLabel("Separator String: ");
        final JTextField multiFrameSeparator = new JTextField(
                configuration.getValue(CONFIG_ELEMENT_PORT_SEPARATOR), 4);

        final JLabel dc6CodecClearIndexLabel = new JLabel("Clear Index: ");
        final JTextField dc6CodecClearIndex = new JTextField(
                String.valueOf(configuration.getValue(CONFIG_ELEMENT_CODEC_DC6_CLEAR_INDEX)), 3);
        final JLabel dccCodecClearIndexLabel = new JLabel("Clear Index: ");
        final JTextField dccCodecClearIndex = new JTextField(
                String.valueOf(configuration.getValue(CONFIG_ELEMENT_CODEC_DCC_CLEAR_INDEX)), 3);

        // Set interface interdependencies
        virtualClearColorEnabled.addActionListener(new ComponentTogglingAction(virtualClearColorButton));
        virtualClearColorEnabled.addActionListener(new ComponentTogglingAction(clearToVirtualOnExport));

        // Set up "What Is This?" listeners
        WhatIsThisMouseListener.batchAdd(
                "Layout Style",
                "The layout style defines how the components in the SixDice frame are laid out.  The default value " +
                "for SixDice is Wide-Left Layout.  The Wide layouts are meant to be easier to use on a larger " +
                "screen, while the Tall layouts are easier to view on a smaller screen.  The sidedness (left or " +
                "right) of the layout defines where the image display panel appears with respect to the rest of the " +
                "controls.",
                layoutLabel, layoutComboBox);
        WhatIsThisMouseListener.batchAdd(
                "Image Background Color",
                "Selects the color used as the background in the display pane.  This is purely a display detail; it " +
                "has nothing to do with the content of the animation file.  It is useful for determining where an " +
                "image stops and the unused space on the image display pane starts.",
                backgroundColorLabel, backgroundColorButton);
        WhatIsThisMouseListener.batchAdd(
                "Image Underlay Color",
                "Selects the color used as the underlay in the display pane.  This is purely a display detail; it " +
                "has nothing to do with the content of the animation file.  The underlay is displayed under the " +
                "image itself; this means that transparent pixels will show as this color instead of the background " +
                "color.",
                underlayColorLabel, underlayColorButton);
        WhatIsThisMouseListener.batchAdd(
                "Clear on Drag-and-Drop Loads",
                "It is possible to load or import a file by dragging it onto the image panel.  For example, " +
                "dragging a JPG from Windows Explorer to SixDice will cause SixDice to import the JPG.  If you " +
                "want SixDice to clear the workspace before doing this, check this box.  If you want to import " +
                "the file into the current workspace, clear this box.",
                dragAndDropImportClearLabel, dragAndDropImportClear);
        WhatIsThisMouseListener.batchAdd(
                "Insert Imported Frames",
                "When a frame is imported, it will either replace the currently-selected frame (if this box is " +
                "clear) or be inserted before the currently selected frame (if this box is checked).",
                insertImportedFramesLabel, insertImportedFrames);
        WhatIsThisMouseListener.batchAdd(
                "Insert Split Frames",
                "Splitting a frame allows it to be divided by a specific size.  For example, Diablo II requires that " +
                "images larger than 256x256 are split into 256x256 tiles.  If this checkbox is selected, the new " +
                "frames created by this operation will be inserted into the location of the old frame.  Otherwise, " +
                "the first new frame will replace the old frame and successive frames will overwrite the following " +
                "frames.  For example, if an animation contains a single direction with the frames A, B, and C, " +
                "frame B could be split in two ways.  If frame B is split using insertion, the new frame list will " +
                "be A, B1, B2, ..., Bn, C.  If frame B is split using overwrite, the new frame list will be " +
                "A, B1, B2, ..., Bn.  Note that if insertion is used, the inserted frames will appear in other " +
                "directions as well as new, blank frames.",
                insertSplitFramesLabel, insertSplitFrames);
        WhatIsThisMouseListener.batchAdd(
                "Reloading Saved Files",
                "The image as it appears in the image display pane is the image that SixDice has in memory (possibly " +
                "filtered through the selected palette); it is not necessarily what will be saved to the disk.  As a " +
                "result, the displayed image may be misleading since, due to compression, the quality of the image " +
                "displayed by SixDice may be higher than that of the saved file.  If this box is checked, any file " +
                "which is saved is immediately reloaded, ensuring that SixDice has the actual contents of the saved " +
                "file in memory.",
                loadOnSaveLabel, loadOnSave);
        WhatIsThisMouseListener.batchAdd(
                "Virtual Clear Color",
                "The virtual clear color is a color which is assumed to be transparent in imported images.  For " +
                "example, one may store image files with a black background with the assumption that black pixels " +
                "in the animation are actually transparent; another common color to use is bright pink (1.0, 0.0, " +
                "1.0), since it isn't commonly used.  Transparent pixels in imported images will still be treated " +
                "as transparent, so this option isn't strictly necessary, but it is often used in practice.",
                virtualClearColorEnabledLabel, virtualClearColorEnabled, virtualClearColorLabel,
                virtualClearColorButton);
        WhatIsThisMouseListener.batchAdd(
                "Clear-to-Virtual Mapping on Export",
                "Since it may be useful to specify a color to be treated as clear in imported images, it would " +
                "often be expected that exported images use that color instead of clear.  If this behavior is " +
                "desired, check this box.  Note that this task is not performed if the virtual clear color is " +
                "disabled.",
                clearToVirtualOnExportLabel, clearToVirtualOnExport);
        WhatIsThisMouseListener.batchAdd(
                "Multi-frame Spearator String",
                "When a multi-frame animation file is exported or imported, such an operation is performed with a " +
                "set of image files.  The image files are named \"filename__x__y.ext\", where " +
                "X and Y are the direction and frame number of that image, respectively.  For exmaple, a " +
                "flippy file may be represented in PNG format by seventeen files, named \"flippy__0__0.png\"" +
                "through \"flippy__0__16.png\".  The string \"__\" represents the separator that is used " +
                "when generating or searching for files; indeed, the separator string is \"__\" by default.  " +
                "If you prefer your multi-frame files to be named differently, feel free to change this " +
                "string.\n\nNote to advanced users: the separator string is processed as a Java regular " +
                "expression.  Therefore, some special characters must be avoided (such as \"(\" and \")\")." +
                "However, for purposes of importing, this fact may be useful.",
                multiFrameSeparatorLabel, multiFrameSeparator);
        WhatIsThisMouseListener.batchAdd(
                "Clear Index",
                "The DCC and DC6 file formats have support for fully opaque indices and a single transparent index.  " +
                "However, there is nothing preventing these file formats from using any index in the palette as " +
                "transparent, as the transparency support is simply in some form of run-length encoding (in the " +
                "pixel data for DC6 files and in the palette data for DCC files).  Diablo II assumes that the " +
                "transparent index for these files is always 0; however, in the event that it needs to be changed " +
                "for another use, that can be done here.",
                dc6CodecClearIndexLabel, dc6CodecClearIndex, dccCodecClearIndexLabel,
                dccCodecClearIndex);

        // Establish button panel and action methods
        final SixDiceFrame scopedThis = this;
        ApprovalButtonPanel buttonPanel = new ApprovalButtonPanel()
        {
            public boolean apply()
            {
                // Validate user input
                String message = null;
                if (!((MathUtilities.isNumber(dccCodecClearIndex.getText(), 10)) &&
                      (MathUtilities.isBoundedBy(
                              Integer.valueOf(dccCodecClearIndex.getText()),
                              CONFIG_ELEMENT_CODEC_DCC_CLEAR_INDEX.getMinimumBound(),
                              CONFIG_ELEMENT_CODEC_DCC_CLEAR_INDEX.getMaximumBound()))))
                {
                    message = "DCC codec clear index must be within [" +
                              CONFIG_ELEMENT_CODEC_DCC_CLEAR_INDEX.getMinimumBound() +
                              ',' +
                              CONFIG_ELEMENT_CODEC_DCC_CLEAR_INDEX.getMaximumBound() +
                              ']';
                }
                if (!((MathUtilities.isNumber(dc6CodecClearIndex.getText(), 10)) &&
                      (MathUtilities.isBoundedBy(
                              Integer.valueOf(dc6CodecClearIndex.getText()),
                              CONFIG_ELEMENT_CODEC_DC6_CLEAR_INDEX.getMinimumBound(),
                              CONFIG_ELEMENT_CODEC_DC6_CLEAR_INDEX.getMaximumBound()))))
                {
                    message = "DC6 codec clear index must be within [" +
                              CONFIG_ELEMENT_CODEC_DC6_CLEAR_INDEX.getMinimumBound() +
                              ',' +
                              CONFIG_ELEMENT_CODEC_DC6_CLEAR_INDEX.getMaximumBound() +
                              ']';
                }
                if (multiFrameSeparator.getText().length() < 1)
                {
                    message = "Separator text cannot be empty.";
                }
                if (message != null)
                {
                    JOptionPane.showMessageDialog(scopedThis, message, "Input Error", JOptionPane.ERROR_MESSAGE);
                    return false;
                }

                // Change stored configuration
                boolean changeLayout = configuration.setValue(
                        CONFIG_ELEMENT_UI_LAYOUT, layoutComboBox.getSelectedIndex());
                configuration.setValue(CONFIG_ELEMENT_UI_IMAGE_BACKGROUND, backgroundColorButton.getColor());
                configuration.setValue(CONFIG_ELEMENT_UI_IMAGE_UNDERLAY, underlayColorButton.getColor());
                configuration.setValue(
                        CONFIG_ELEMENT_UI_CLEAR_ON_DND_IMPORTS, dragAndDropImportClear.isSelected());
                configuration.setValue(CONFIG_ELEMENT_IMPORT_INSERTS, insertImportedFrames.isSelected());
                configuration.setValue(CONFIG_ELEMENT_FRAME_SPLIT_INSERTS, insertSplitFrames.isSelected());
                configuration.setValue(
                        CONFIG_ELEMENT_IMPORT_VIRTUAL_CLEAR_COLOR,
                        virtualClearColorEnabled.isSelected() ? virtualClearColorButton.getColor() : null);
                configuration.setValue(
                        CONFIG_ELEMENT_EXPORT_CLEAR_TO_VIRTUAL,
                        virtualClearColorEnabled.isSelected() && clearToVirtualOnExport.isSelected());
                configuration.setValue(CONFIG_ELEMENT_PORT_SEPARATOR, multiFrameSeparator.getText());
                configuration.setValue(
                        CONFIG_ELEMENT_CODEC_DC6_CLEAR_INDEX, Integer.valueOf(dc6CodecClearIndex.getText()));
                configuration.setValue(
                        CONFIG_ELEMENT_CODEC_DCC_CLEAR_INDEX, Integer.valueOf(dccCodecClearIndex.getText()));
                configuration.setValue(CONFIG_ELEMENT_UI_SAVED_FILES_RELOADED, loadOnSave.isSelected());

                // Apply changes
                applyConfiguration(changeLayout);

                return true;
            }

            public void close()
            {
                preferencesDialog.setVisible(false);
                preferencesDialog.dispose();
            }
        };

        preferencesDialog.setContentPane(
                new ComponentConstructorPanel(
                        new SpongyLayout(SpongyLayout.Orientation.VERTICAL),
                        new Pair<JComponent, Object>(
                                new ContentConstructorTabbedPane(
                                        new ContentConstructorTabbedPane.TabData(
                                                "User Interface Preferences",
                                                new ComponentConstructorPanel(
                                                        new SingleComponentPositioningLayout(0.5, 0.0),
                                                        new ComponentConstructorPanel(
                                                                new InformalGridLayout(2, 3, 2, 2, false),
                                                                layoutLabel,
                                                                layoutComboBox,
                                                                backgroundColorLabel,
                                                                backgroundColorButton,
                                                                underlayColorLabel,
                                                                underlayColorButton))),
                                        new ContentConstructorTabbedPane.TabData(
                                                "File Input/Output Settings",
                                                new ComponentConstructorPanel(
                                                        new SingleComponentPositioningLayout(0.5, 0.0),
                                                        new ComponentConstructorPanel(
                                                                new SpongyLayout(SpongyLayout.Orientation.VERTICAL),
                                                                new ComponentConstructorPanel(
                                                                        new InformalGridLayout(2, 4, 2, 2, false),
                                                                        dragAndDropImportClearLabel,
                                                                        dragAndDropImportClear,
                                                                        insertImportedFramesLabel,
                                                                        insertImportedFrames,
                                                                        insertSplitFramesLabel,
                                                                        insertSplitFrames,
                                                                        loadOnSaveLabel,
                                                                        loadOnSave).setBorderAndReturn(
                                                                        new TitledBorder(
                                                                                new EtchedBorder(),
                                                                                "Interface Behavior")),
                                                                new ComponentConstructorPanel(
                                                                        new InformalGridLayout(2, 3, 2, 2, false),
                                                                        virtualClearColorEnabledLabel,
                                                                        virtualClearColorEnabled,
                                                                        virtualClearColorLabel,
                                                                        virtualClearColorButton,
                                                                        clearToVirtualOnExportLabel,
                                                                        clearToVirtualOnExport).setBorderAndReturn(
                                                                        new TitledBorder(
                                                                                new EtchedBorder(),
                                                                                "Image Translation")),
                                                                new ComponentConstructorPanel(
                                                                        new InformalGridLayout(2, 1, 2, 2, false),
                                                                        multiFrameSeparatorLabel,
                                                                        multiFrameSeparator).setBorderAndReturn(
                                                                        new TitledBorder(
                                                                                new EtchedBorder(),
                                                                                "Other Settings"))))),
                                        new ContentConstructorTabbedPane.TabData(
                                                "Codec Settings",
                                                new ComponentConstructorPanel(
                                                        new SingleComponentPositioningLayout(0.5, 0.0),
                                                        new ComponentConstructorPanel(
                                                                new SpongyLayout(SpongyLayout.Orientation.VERTICAL),
                                                                new ComponentConstructorPanel(
                                                                        new InformalGridLayout(2, 1, 2, 2, false),
                                                                        dc6CodecClearIndexLabel,
                                                                        dc6CodecClearIndex).setBorderAndReturn(
                                                                        new TitledBorder(
                                                                                new EtchedBorder(),
                                                                                "DC6 Codec")),
                                                                new ComponentConstructorPanel(
                                                                        new InformalGridLayout(2, 1, 2, 2, false),
                                                                        dccCodecClearIndexLabel,
                                                                        dccCodecClearIndex).setBorderAndReturn(
                                                                        new TitledBorder(
                                                                                new EtchedBorder(),
                                                                                "DCC Codec")))))),
                                SpongyLayout.PRIORITY_NORMAL),
                        new Pair<JComponent, Object>(
                                buttonPanel,
                                SpongyLayout.PRIORITY_PREFERRED)));

        preferencesDialog.pack();
        preferencesDialog.setLocationRelativeTo(this);
        preferencesDialog.setVisible(true);
    }

    /**
     * Used to propogate values from the {@link Configuration} object to the various UI components.  Some values in the
     * {@link Configuration} object must be applied to other elements of the UI (such as the image display component)
     * since those elements do not actively read the {@link Configuration} object itself.
     *
     * @param changeLayout <code>true</code> if the layout style should be updated; <code>false</code> to skip this
     *                     step.  This may be desirable as the layout style change causes a pack.
     */
    protected void applyConfiguration(boolean changeLayout)
    {
        if (changeLayout)
        {
            setContentPane(configuration.getValue(CONFIG_ELEMENT_UI_LAYOUT));
        }
        imageComponent.setBackground(configuration.getValue(CONFIG_ELEMENT_UI_IMAGE_BACKGROUND));
        imageComponent.setUnderlay(configuration.getValue(CONFIG_ELEMENT_UI_IMAGE_UNDERLAY));
        core.setVirtualTransparentColor(configuration.getValue(CONFIG_ELEMENT_IMPORT_VIRTUAL_CLEAR_COLOR));
        core.setTransparentToVirtualOnSave(configuration.getValue(CONFIG_ELEMENT_EXPORT_CLEAR_TO_VIRTUAL));
    }

    /**
     * Retrieves the current palette.
     *
     * @return The palette currently selected in the interface.
     */
    protected RestrictableIndexColorModel getPalette()
    {
        //noinspection SuspiciousMethodCalls
        RestrictableIndexColorModel model = Diablo2DefaultPalettes.PALETTE_MAP.get(
                paletteBox.getModel().getSelectedItem());
        assert(model != null);
        // Do we really want to do this?  Is there another way to get the remapped image to display with proper clear
        // pixels?
        model = model.deriveWithTransparentInices(0);
        return model;
    }

    /**
     * Resets the direction and frame lists.
     */
    protected void resetListSelection()
    {
        ((IntegerRangeListModel) (directionList.getModel())).setMaximum(
                (core.getAnimation() == null) ? 0 : core.getAnimation().getDirectionCount());
        ((IntegerRangeListModel) (frameList.getModel())).setMaximum(
                (core.getAnimation() == null) ?
                0 :
                core.getAnimation().getFrameCount());
        if (directionList.getModel().getSize() > 0) directionList.setSelectedIndex(0);
        if (frameList.getModel().getSize() > 0) frameList.setSelectedIndex(0);
    }

    /**
     * Updates the currently displayed frame.
     */
    protected void updateDisplay()
    {
        if (core.getAnimation() != null)
        {
            if (directionList.getModel().getSize() != core.getAnimation().getDirectionCount())
            {
                ((IntegerRangeListModel) (directionList.getModel())).setMaximum(
                        core.getAnimation().getDirectionCount());
            }
            if (frameList.getModel().getSize() != core.getAnimation().getFrameCount())
            {
                ((IntegerRangeListModel) (frameList.getModel())).setMaximum(core.getAnimation().getFrameCount());
            }
            if ((frameList.getSelectedIndex() == -1) && (core.getAnimation().getFrameCount() > 0))
            {
                frameList.setSelectedIndex(0);
            }
            if ((directionList.getSelectedIndex() == -1) && (core.getAnimation().getDirectionCount() > 0))
            {
                directionList.setSelectedIndex(0);
            }
            if ((frameList.getSelectedIndex() >= core.getAnimation().getFrameCount()))
            {
                frameList.setSelectedIndex(0);
            }
            if ((directionList.getSelectedIndex() >= core.getAnimation().getDirectionCount()))
            {
                directionList.setSelectedIndex(0);
            }
        }
        if ((core.getAnimation() == null) || (frameList.getSelectedIndex() == -1) ||
            (directionList.getSelectedIndex() == -1))
        {
            imageComponent.setImage(splashImage);
            imageScrollPane.getViewport().setView(imageComponent);
            offsetX.setText("-");
            offsetY.setText("-");
            labelWidth.setText("-");
            labelHeight.setText("-");
            offsetX.setEnabled(false);
            offsetY.setEnabled(false);
            ((IntegerRangeListModel) (directionList.getModel())).setMaximum(0);
            ((IntegerRangeListModel) (frameList.getModel())).setMaximum(0);
            warningsPanel.setPainted(false);
        } else
        {
            final AnimationFrame frame = core.getAnimation().getFrame(
                    directionList.getSelectedIndex(), frameList.getSelectedIndex());
            offsetX.setText(Integer.toString(frame.getXOffset()));
            offsetY.setText(Integer.toString(frame.getYOffset()));
            labelWidth.setText(Integer.toString(frame.getImage().getWidth()));
            labelHeight.setText(Integer.toString(frame.getImage().getHeight()));
            if (configuration.getValue(CONFIG_ELEMENT_UI_IMAGE_RENDERED_IN_PALETTE))
            {
                new Thread("Waiting Dialog")
                {
                    public void run()
                    {
                        waitingDialogDisplayer.setVisible(true);
                        try
                        {
                            imageComponent.setImage(getPalette().redraw(frame.getImage()));
                        } finally
                        {
                            waitingDialogDisplayer.setVisible(false);
                        }
                        imageScrollPane.getViewport().setView(imageComponent);
                    }
                }.start();
            } else
            {
                imageComponent.setImage(frame.getImage());
                imageScrollPane.getViewport().setView(imageComponent);
            }
            offsetX.setEnabled(true);
            offsetY.setEnabled(true);
            ((IntegerRangeListModel) (directionList.getModel())).setMaximum(
                    core.getAnimation().getDirectionCount());
            ((IntegerRangeListModel) (frameList.getModel())).setMaximum(core.getAnimation().getFrameCount());
            warningsPanel.setPainted(core.getAnimation().getWarnings().length > 0);
        }
    }

// STATIC METHODS ////////////////////////////////////////////////////////////////

}

// END OF FILE