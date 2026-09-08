# screenshot-find-image

Screenshot and image bridge example.

The script requests Android screen capture consent. Select the entire screen and allow capture. It saves `screen.png`, clips a 160x120 region in the center (or less on a smaller display), saves and reads back `template.png`, and finds that template in the original screenshot. Prefer a screen with distinctive content: a uniform region can match more than one position.

The host OpenCV plugin is required for template matching. Denied capture or missing OpenCV produces a readable skipped marker. Other errors fail the example. Image handles and the capture session are released in `finally`. The automated image fixture checks pass; the screenshot acceptance check still awaits manual consent, so this example remains partial.

Expected output is listed in `expected-output.txt`.
