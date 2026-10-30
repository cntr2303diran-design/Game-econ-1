package com.example.ui

import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tata letak lebar (tablet/lanskap, ≥700dp): menjalankan uji dasar yang sama pada layar 900x560dp. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w900dp-h560dp-land-xhdpi")
class TabletUiTest : UiTestBase() {
    override val shot = "tablet"
}
