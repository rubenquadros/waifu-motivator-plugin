package zd.zero.waifu.motivator.plugin.alert.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.JBColor;
import com.intellij.util.ResourceUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zd.zero.waifu.motivator.plugin.providers.UniqueValueProvider;
import zd.zero.waifu.motivator.plugin.settings.WaifuMotivatorPluginState;
import zd.zero.waifu.motivator.plugin.settings.WaifuMotivatorState;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Random;

public class WaifuOfTheDayDialog extends DialogWrapper {

    private static final Logger LOGGER = Logger.getInstance( WaifuOfTheDayDialog.class );

    private static final String KEY_IS_DIALOG_SHOWN_TODAY = "WMP_KEY_IS_DIALOG_SHOWN_TODAY";

    private static final String KEY_RECENT_WAIFU = "WMP_KEY_RECENT_WAIFU";

    private static final String WAIFU_OF_THE_DAY_BASE_PATH = "/waifu_of_the_day/";

    private static final String WAIFU_OF_THE_DAY_IMAGE_PATH = WAIFU_OF_THE_DAY_BASE_PATH + "images/";

    private static final String WAIFU_OF_THE_DAY_TEMPLATE = "WaifuOfTheDayTemplate.html";

    private static final String WAIFU_OF_THE_DAY_STYLE = "style.css";

    private static final String WAIFU_OF_THE_DAY_CONTENT = "waifu.json";

    private static final String USED_WAIFU_OF_THE_DAY = "WMP_USED_WAIFU_OF_THE_DAY";

    private static final int DEFAULT_WIDTH = 600;

    private static final int DEFAULT_HEIGHT = 700;

    private static WaifuOfTheDayDialog waifuOfTheDayDialog;

    private static WaifuOfTheDay[] waifuOfTheDays;

    private static WaifuOfTheDay currentWaifuOfTheDay;

    private JPanel rootPanel;

    private JScrollPane scrollPane;

    private JEditorPane browser;

    private UniqueValueProvider<WaifuOfTheDay> uniqueValueProvider;

    private Random random;

    public WaifuOfTheDayDialog( @NotNull final Window parent ) {
        super( parent, true );
        initializeComponents();
        initializeContent();

        setModal( false );
        setTitle( "Waifu of the Day" );
        setCancelButtonText( CommonBundle.getCloseButtonText() );
        setDoNotAskOption( new DialogTipPanel() );

        init();
    }

    public static void canBeShownToday( Project project ) {
        WaifuMotivatorState pluginState = WaifuMotivatorPluginState.getPluginState();
        boolean canBeShownToday = pluginState != null && pluginState.isWaifuOfTheDayEnabled() &&
                !GeneralSettings.getInstance().isShowTipsOnStartup() && !isDialogShownToday();
        if ( canBeShownToday ) {
            show( project );
        }
    }

    public static void show( Project project ) {
        Window parentWindow = WindowManagerEx.getInstanceEx().suggestParentWindow( project );
        if ( parentWindow == null ) parentWindow = WindowManagerEx.getInstanceEx().findVisibleFrame();
        if ( waifuOfTheDayDialog != null && waifuOfTheDayDialog.isVisible() ) {
            waifuOfTheDayDialog.dispose();
        }
        waifuOfTheDayDialog = new WaifuOfTheDayDialog( parentWindow );
        waifuOfTheDayDialog.show();
        PropertiesComponent.getInstance().setValue( KEY_IS_DIALOG_SHOWN_TODAY,
                String.valueOf( System.currentTimeMillis() ) );
    }

    private static boolean isDialogShownToday() {
        try {
            return System.currentTimeMillis() - Long.parseLong( PropertiesComponent.getInstance()
                    .getValue( KEY_IS_DIALOG_SHOWN_TODAY, "0" ) ) < DateFormatUtil.DAY;
        } catch ( NumberFormatException e ) {
            return true;
        }
    }

    private void initializeComponents() {
        scrollPane.setBorder( JBUI.Borders.customLine( new JBColor( 0xd9d9d9, 0x515151 ),
                0, 0, 1, 0 ) );
        browser.setBackground( null );
        browser.setPreferredSize( new JBDimension( DEFAULT_WIDTH, DEFAULT_HEIGHT ) );
        browser.addHyperlinkListener( e -> {
            if ( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
                BrowserUtil.browse( e.getURL() );
            }
        } );
    }

    private void initializeContent() {
        try {
            String recentWaifu = PropertiesComponent.getInstance().getValue( KEY_RECENT_WAIFU, "" );
            if ( isDialogShownToday() && StringUtils.isNotEmpty( recentWaifu ) ) {
                currentWaifuOfTheDay = getWaifuOfTheDayByName( recentWaifu );
            } else {
                currentWaifuOfTheDay = getWaifuOfTheDayRandom();
                PropertiesComponent.getInstance().setValue( KEY_RECENT_WAIFU, currentWaifuOfTheDay.getName() );
            }

            URL imageResource = ResourceUtil.getResource( getClass().getClassLoader(),
                    WAIFU_OF_THE_DAY_IMAGE_PATH, currentWaifuOfTheDay.getImage() );

            String content = getTemplateContent()
                    .replace( "{{name}}", StringUtils.defaultString( currentWaifuOfTheDay.getName() ) )
                    .replace( "{{anime}}", StringUtils.defaultString( currentWaifuOfTheDay.getAnime() ) )
                    .replace( "{{image}}", imageResource.toExternalForm() )
                    .replace( "{{sourceUrl}}", StringUtils.defaultIfEmpty( currentWaifuOfTheDay.getSourceUrl(), "#" ) )
                    .replace( "{{style}}", "<style type=\"text/css\">" + getCssContent() + "</style>" )
                    .replace( "{{description}}", StringUtils.defaultString( currentWaifuOfTheDay.getDescription() )
                            .replace( "\n", "<br/>" ) );

            this.browser.setText( content );
            this.browser.setCaretPosition( 0 ); // resets scroll back to top
        } catch ( IOException e ) {
            LOGGER.error( e.getMessage(), e );
        }
    }

    private WaifuOfTheDay[] getWaifuOfTheDay() throws IOException {
        if ( waifuOfTheDays == null ) {
            ClassLoader classLoader = getClass().getClassLoader();
            try ( InputStream resource = ResourceUtil.getResourceAsStream( classLoader,
                    WAIFU_OF_THE_DAY_BASE_PATH, WAIFU_OF_THE_DAY_CONTENT ) ) {
                if ( resource == null ) {
                    throw new IOException( "Cannot find the waifu content." );
                }

                ObjectMapper mapper = new ObjectMapper();
                waifuOfTheDays = mapper.readValue( resource, WaifuOfTheDay[].class );
            }
        }

        return waifuOfTheDays;
    }

    private WaifuOfTheDay getWaifuOfTheDayRandom() throws IOException {
        if ( uniqueValueProvider == null ) {
            uniqueValueProvider = new UniqueValueProvider<>( USED_WAIFU_OF_THE_DAY );
        }

        WaifuOfTheDay[] waifuOfTheDays = uniqueValueProvider
                .getUniqueValues( getWaifuOfTheDay(), WaifuOfTheDay::getName ).toArray( new WaifuOfTheDay[0] );

        WaifuOfTheDay theDay;
        if ( waifuOfTheDays.length == 1 ) {
            theDay = waifuOfTheDays[0];
        } else {
            random = random == null ? new Random() : random;
            int randomIndex = random.nextInt( waifuOfTheDays.length );
            theDay = waifuOfTheDays[randomIndex];
        }

        uniqueValueProvider.addToSeenValues( theDay.getName() );

        return theDay;
    }

    private WaifuOfTheDay getWaifuOfTheDayByName( String name ) throws IOException {
        if ( name.isEmpty() ) {
            return getWaifuOfTheDayRandom();
        }

        return Arrays.stream( getWaifuOfTheDay() )
                .filter( w -> w.getName().equals( name ) )
                .findFirst()
                .orElse( getWaifuOfTheDayRandom() );
    }

    private String getContent( String fileName ) throws IOException {
        try ( InputStream resource = ResourceUtil.getResourceAsStream( getClass().getClassLoader(),
                WAIFU_OF_THE_DAY_BASE_PATH, fileName ) ) {
            if ( resource == null ) {
                return "";
            }

            return ResourceUtil.loadText( resource );
        }
    }

    private String getTemplateContent() throws IOException {
        return getContent( WAIFU_OF_THE_DAY_TEMPLATE );
    }


    private String getCssContent() throws IOException {
        return getContent( WAIFU_OF_THE_DAY_STYLE );
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return this.rootPanel;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        return new Action[]{ getViewAnimeAction(), getCancelAction() };
    }

    private AbstractAction getViewAnimeAction() {
        return new AbstractAction() {
            {
                putValue( Action.NAME, "View &Anime" );
            }

            @Override
            public void actionPerformed( ActionEvent e ) {
                String url = currentWaifuOfTheDay.getAnimeUrl();
                if ( currentWaifuOfTheDay != null && StringUtils.isNotEmpty( url ) ) {
                    BrowserUtil.browse( url );
                } else {
                    throw new WaifuMotivatorViewAnimeException(
                            "Cannot view current Waifu Of The Day: '" + currentWaifuOfTheDay + "'" );
                }
            }
        };
    }
}