# Debugging LoudnessControllerActivity Crash

## Summary
The LoudnessControllerActivity crashes immediately on launch. I've created several test versions to help isolate the issue.

## Test Activities Created

1. **LoudnessControllerActivityWrapper** - A test launcher that provides buttons to test different versions
2. **LoudnessControllerActivityMinimal** - Extends AppCompatActivity directly (bypasses BaseActivity)
3. **LoudnessControllerActivitySafe** - Uses lazy initialization for all components
4. **LoudnessControllerActivityNoDI** - Completely bypasses BaseActivity and dependency injection
5. **LoudnessControllerActivityDebug** - Extends BaseActivity but with extensive logging

## Modified Files

1. **MainActivity.kt** - Modified to launch the test wrapper instead of the original activity
2. **LoudnessControllerActivity.kt** - Added extensive logging to track initialization
3. **AndroidManifest.xml** - Added all test activities

## Testing Steps

1. Build and run the app
2. Navigate to the Loudness Controller menu item
3. You'll see the test wrapper with multiple buttons
4. Try each button in order:
   - "Test Original LoudnessControllerActivity" - This should crash
   - "Test Safe LoudnessControllerActivity" - This might work with minimal functionality
   - "Test Minimal (No BaseActivity)" - This should definitely work
   - "Test No DI Version" - This should also work
   - "Test Class Loading" - This tests if the class can even be loaded

## What to Look For in Logs

Filter logs by "LoudnessControllerActivity" or "LoudnessDebug" to see:
- Which initialization step causes the crash
- Whether it's a class loading issue or runtime issue
- If it's related to BaseActivity, dependency injection, or view initialization

## Possible Causes

1. **Dependency Injection Issue** - BaseActivity uses Koin for DI which might not be initialized
2. **View Binding Issue** - The layout file might have issues
3. **Static Initialization** - The companion object's preampTable might cause issues
4. **Missing Resources** - Some string or drawable resources might be missing
5. **Theme/Style Issue** - The activity theme might have problems

## Next Steps Based on Results

- If minimal version works but safe doesn't: Issue is with BaseActivity
- If safe works but original doesn't: Issue is with eager initialization
- If none work: Issue might be with the layout file or resources
- If class loading fails: Issue is at the bytecode/compilation level

## To Restore Original Behavior

Once debugging is complete, revert MainActivity.kt:
```kotlin
R.id.action_loudness_controller -> {
    startActivity(Intent(this, LoudnessControllerActivity::class.java))
    true
}
```

And optionally remove the test activities from the manifest and delete the test files.