## Flamingo - commands

A command is the most basic building block of Flamingo components. Let's take a look at this screenshot of the main Flamingo ribbon demo application:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/ribbon-lego.jpg" width="767" border=0/>

Apart from the two comboboxes in the main content area on the right, and another combobox in the title area, all the other components in this screen are built from commands. In fact, even those comboboxes could be replaced with commands, and the only reason to have them in this demo is to show that ribbon can host regular Swing components (such as comboboxes, spinners, etc). But more on that later.

### Attributes overview

Commands are created with the builder pattern which is pervasive throughout Flamingo. Call `Command.builder()` to get a new builder instance. Then, configure one or more of the following attributes on the builder:

|  | Attribute | Dynamic? |
| --- | --- | --- |
| **Base** | text | yes |
|  | extraText | yes |
|  | iconFactory | yes |
|  | disabledIconFactory | yes |
| **Action** | action | yes |
|  | actionPreview | yes |
|  | actionRichTooltip | yes |
|  | isActionEnabled | yes |
|  | isTextClickAction | - |
|  | isAutoRepeatAction | yes |
|  | autoRepeatInitialInterval | - |
|  | autoRepeatSubsequentInterval | - |
|  | isFireActionOnRollover | yes |
|  | isFireActionOnPress | yes |
| **Secondary**  | secondaryContentModel | - |
|  | secondaryRichTooltip | yes |
|  | isSecondaryEnabled | yes |
|  | isTextClickSecondary | - |
| **Toggle**  | isToggle | - |
|  | isToggleSelected | yes |
|  | toggleGroupModel | - |

### Base attributes

Let's take a look at the following screenshot that shows how four commands (paste, cut, copy, and select all) might be rendered on the screen (or projected, in Flamingo terminology):

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-basics.png" width="780" border=0/>

In each column, all four buttons were projected from the same command. The only difference is the presentation model associated with each one of the projection:

1. In the first row (big state), the button is showing the text (that might go to two lines) and a big icon, stacked vertically.
2. In the second row (tile state), the big icon is on the left, and the vertical stack on the right displays the text and the extra text.
3. In the third row (medium state), the icon is smaller, and only text is showing.
4. In the fourth row (small state), only the small icon is showing.

For the paste buttons (first column), the command that was used to project all four buttons looks like this:

```java
this.pasteActionCommand = Command.builder()
    .setText(resourceBundle.getString("Paste.text"))
    .setExtraText(resourceBundle.getString("Paste.textExtra"))
    .setIconFactory(Edit_paste.factory())
    .build();
```

As with all code samples in Flamingo documentation, the classes for icons passed to `setIconFactory()` API calls were transcoded by [Photon](../photon/photon.md).

### Action attributes

#### Action

Action is a piece of code associated with a command that is executed when that command is activated (with mouse or keyboard):

```java
// Align fill command
Command commandAlignFill = Command.builder()
        .setIconFactory(Format_justify_fill.factory())
        .setAction((CommandActionEvent event) ->
                setAlignment(textPane, StyleConstants.ALIGN_JUSTIFIED))
        .build();
```

The `CommandAction` instance passed to `setAction()` looks similar to the core Swing `ActionListener`:

```java
public interface CommandAction extends EventListener {
    /** Invoked when a command is activated. */
    void commandActivated(CommandActionEvent e);
}
```

#### Action preview

In addition, you can call `setActionPreview()` to configure the action preview. The `CommandActionPreview` interface looks like this:

```java
public interface CommandActionPreview extends EventListener {
    /** Invoked when a command preview has been activated. */
    void onCommandPreviewActivated(Command command);

    /** Invoked when a command preview has been canceled. */
    void onCommandPreviewCanceled(Command command);
}
```

Command preview is activated when the command's projection goes into the preview mode - in most cases it would be when the user moves the mouse over the projected button. For example, the same "fill alignment" command above might provide a preview of how that mode looks like without fully "committing" the user to take the corresponding action. Preview mode should provide the complete preview of the command's action, and fully rollback that preview once that mode is canceled. If implemented properly, preview mode is a powerful feature that would allow your user to explore the functionality of the application without the extra overhead of clicking around the controls and trying to undo the resulting operations explicitly.

#### Action rich tooltip

Rich tooltips are shown on hover, providing the opportunity to explain what the corresponding command does:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-tooltips.png" width="782" border=0/>

To configure the rich tooltip for the command's action, use `RichTooltip.Builder` and `setActionRichTooltip()` APIs:

```java
this.pasteActionCommand = Command.builder()
    .setText(resourceBundle.getString("Paste.text"))
    .setExtraText(resourceBundle.getString("Paste.textExtra"))
    .setIconFactory(Edit_paste.factory())
    .setAction((CommandActionEvent e) -> System.out.println("Paste activated"))
    .setActionRichTooltip(RichTooltip.builder()
          .setTitle(resourceBundle.getString("Tooltip.textActionTitle"))
          .addDescriptionSection(resourceBundle.getString("Tooltip.textParagraph1"))
          .addDescriptionSection(resourceBundle.getString("Tooltip.textParagraph2"))
          .setMainIconFactory(Address_book_new.factory())
          .setFooterIconFactory(Help_browser.factory())
          .addFooterSection(resourceBundle.getString("Tooltip.textFooterParagraph1"))
          .build())
    .build();
```

#### Enabling and disabling

The command's action can be disabled and enabled dynamically based on application-specific logic. For example, commands that toggle styling of the selected content in a `JTextPane` might have their action marked as disabled when there is no selection by calling `setActionEnabled(false)` during the builder initialization:

```java
// Bold style command
Command commandBold = Command.builder()
        .setIconFactory(Format_text_bold.factory())
        .setAction((CommandActionEvent event) -> ...)
        .setToggle(true)
        .setActionEnabled(false)
        .build();
```

and then be dynamically enabled or disabled based on the current selection:

```java
textPane.addCaretListener((CaretEvent e) -> {
    // Compute selection presence
    boolean hasSelection =
            (textPane.getSelectionEnd() - textPane.getSelectionStart()) > 0;
    // Enable or disable the style commands based on that
    commandBold.setActionEnabled(hasSelection);
    ...
});
```

#### Mark text area for invoking action

Let's take a look at two screenshots. In the first one, the mouse cursor is over the text area of the "Cut" button in tile state. Note that different strength of yellow rollover highlight indication. The area of the button that contains the icon and the two texts has a stronger yellow highlight, while the area with the down arrow has a weaker highlight. This button is configured with `setTextClickAction()` API to indicate that clicking anywhere in the area that shows the command text (and extra text, if relevant) will activate the main command action:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-title-action.png" width="764" border=0/>

In the second one, the mouse cursor is over the same text area, this time of the "Copy" button in tile state. Here, the area of the button with the icon has a weaker highlight, while the area with the two texts and the down arrow has a stronger highlight.  This button is configured with `setTextClickSecondary()` API to indicate that clicking anywhere in the area that shows the command text (and extra text, if relevant) will activate the secondary command content - in this case, showing a popup menu:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-title-popup.png" width="764" border=0/>

#### Repeated action

In some cases, the design calls for facilitating repeated activation of the command action. For example, it would be quite tedious to scroll down a large list of items by repeatedly clicking the down button (or area below the scrollbar thumb). The usability of such actions can be improved if, pressed once, the action is repeated continuously until the mouse button is released.

Flamingo commands come with five attributes that aim to address such scenarios.

* `setAutoRepeatAction(true)` will result in a repeated, continuous activation of the command action as long as the projected button is activated.
* `setFireActionOnRollover(true)` will result in command action activation when the mouse is moved over the projected button - without the need to press the mouse button itself.
* Alternatively, `setFireActionOnPress(true)` will result in command action activation when the mouse button is pressed - as opposed to the usual click which is a combination of pressing the button and then releasing it.
* Finally, `setAutoRepeatActionIntervals()` can be used to configure the command-specific initial and subsequent intervals between action activation. The static `Command.DEFAULT_AUTO_REPEAT_*` constants can be used to check for the default values of these two intervals.

### Secondary content attributes

#### Secondary content model

Secondary content allows logical grouping of multiple commands that are only shown when a specific, so-called "secondary" area of the projected button is activated.

The simplest case of secondary content is additional commands shown in a popup menu:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-secondary-simple.png" width="734" border=0/>

Secondary content can be configured to display a certain maximum number of commands on the screen, kicking in vertical scrolling:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-secondary-scrollable.png" width="734" border=0/>

Or have a more complex structure, with an embedded, separately scrollable panel of commands:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-secondary-complex.png" width="734" border=0/>

All these three examples would be called "popup buttons" in similar component suites. The power of secondary content in Flamingo commands can be seen in how easily it is to configure a projected button to be a "regular" action button - with just one action.

Or, by calling `setSecondaryContentModel()` and `setTextClickAction(true)` make it a split button with a popup menu shown when the down arrow is clicked:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-title-action.png" width="764" border=0/>

Or instead, calling `setTextClickSecondary(true)` to make it a split button with a popup menu shown when either texts or down arrow are clicked:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-title-popup.png" width="764" border=0/>

An important note is in order here. Even though all the examples so far have shown secondary content displayed as a popup menu, that is not necessarily the case. Flamingo's model of separating content from presentation (and combining the two in a projection) means that the **same exact command** projected as a split button can be projected into something that looks like this:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/ribbon-application-menu.png" width="754" border=0/>

Here, the ribbon application menu is a two-panel layout. The main commands are projected in the left column. The secondary content associated with a command is displayed in the panel on the right - not as a separate popup menu, but as part of the same application menu container.

#### Secondary rich tooltip

Same as with action rich tooltips, you can configure a rich tooltip to be shown on hovering over the secondary activation area. To configure the rich tooltip for the command's secondary content, use `RichTooltip.Builder` and `setSecondaryRichTooltip` APIs.

#### Enabling and disabling

Secondary content of a command can be enabled and disabled separately from the command's action enabled state. Call `setSecondaryEnabled(false)` to disable secondary content during the builder initialization, or pass `false` / `true` later on to dynamically toggle the enabled state of the secondary content area of the projected button based on application-specific logic.

#### Mark text area for invoking secondary content

This has been mentioned a couple of times earlier in this page. Use `setTextClickSecondary()` API to indicate that clicking anywhere in the area that shows the command text (and extra text, if relevant) will activate the secondary command content - in this case, showing a popup menu:

<img src="https://raw.githubusercontent.com/kirill-grouchnikov/radiance/master/docs/images/flamingo/walkthrough/command-title-popup.png" width="764" border=0/>

### Toggle attributes

A command configured with one of the toggle attributes can be - programmatically or via user interaction with its button projection - in either selected (on) or unselected (off) state.

In the following example we use `inToggleGroup()` and `inToggleGroupAsSelected()` APIs to mark the four commands as toggleable **and** belonging to the same `CommandToggleGroupModel`:

```java
CommandToggleGroupModel justifyToggleGroup = new CommandToggleGroupModel();

// Align left command
Command commandAlignLeft = Command.builder()
        .setIconFactory(Format_justify_left.factory())
        .setAction((CommandActionEvent event) ->
                setAlignment(textPane, StyleConstants.ALIGN_LEFT))
        .inToggleGroupAsSelected(justifyToggleGroup)
        .build();

// Align center command
Command commandAlignCenter = Command.builder()
        .setIconFactory(Format_justify_center.factory())
        .setAction((CommandActionEvent event) ->
                setAlignment(textPane, StyleConstants.ALIGN_CENTER))
        .inToggleGroup(justifyToggleGroup)
        .build();

// Align right command
Command commandAlignRight = Command.builder()
        .setIconFactory(Format_justify_right.factory())
        .setAction((CommandActionEvent event) ->
                setAlignment(textPane, StyleConstants.ALIGN_RIGHT))
        .inToggleGroup(justifyToggleGroup)
        .build();

// Align fill command
Command commandAlignFill = Command.builder()
        .setIconFactory(Format_justify_fill.factory())
        .setAction((CommandActionEvent event) ->
                setAlignment(textPane, StyleConstants.ALIGN_JUSTIFIED))
        .inToggleGroup(justifyToggleGroup)
        .build();
```

When these commands are used to project a button strip, only one of the four resulting buttons can ever be in the selected / on state. When one of the button transitions into such a state, previously selected button becomes unselected / off. This behavior is built into the `CommandToggleGroupModel` class.

In the following example we use `setToggle()` API to mark the four commands as toggleable - separately from each other:

```java
// Bold style command
Command commandBold = Command.builder()
        .setIconFactory(Format_text_bold.factory())
        .setAction((CommandActionEvent event) -> ...)
        .setToggle(true)
        .build();

// Italic style command
Command commandItalic = Command.builder()
        .setIconFactory(Format_text_italic.factory())
        .setAction((CommandActionEvent event) -> ...)
        .setToggle(true)
        .build();

// Underline style command
Command commandUnderline = Command.builder()
        .setIconFactory(Format_text_underline.factory())
        .setAction((CommandActionEvent event) -> ...)
        .setToggle(true)
        .build();

// Strikethrough style command
Command commandStrikethrough = Command.builder()
        .setIconFactory(Format_text_strikethrough.factory())
        .setAction((CommandActionEvent event) -> ...)
        .setToggle(true)
        .build();
```

We then wire the text pane selection listener to update the toggled / selected state of each command based on the application-specific logic (presence of the corresponding style in the selected content):

```java
textPane.addCaretListener((CaretEvent e) -> {
    ...
    // For each command, determine whether its toggle selection is "on" based on
    // the presence of the matching style in the text pane selection
    commandBold.setToggleSelected(hasStyleInSelection(textPane,
            StyleConstants.CharacterConstants.Bold));
    commandItalic.setToggleSelected(hasStyleInSelection(textPane,
            StyleConstants.CharacterConstants.Italic));
    commandUnderline.setToggleSelected(hasStyleInSelection(textPane,
            StyleConstants.CharacterConstants.Underline));
    commandStrikethrough.setToggleSelected(hasStyleInSelection(textPane,
            StyleConstants.CharacterConstants.StrikeThrough));
});
```

And in addition, update each command to flip its toggled state whenever it is activated:

```java
// Bold style command
Command commandBold = Command.builder()
        ...
        .setAction((CommandActionEvent event) -> {
            // toggle bold in current selection
            toggleStyleInSelection(textPane, StyleConstants.CharacterConstants.Bold);
            // and update command selection state based on the presence of bold
            event.getCommand().setToggleSelected(
                    hasStyleInSelection(textPane,
                            StyleConstants.CharacterConstants.Bold));
        })
        ...
        .build();
```