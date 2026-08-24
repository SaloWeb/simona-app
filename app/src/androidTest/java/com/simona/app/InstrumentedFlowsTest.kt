package com.simona.app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentedFlowsTest {

    private lateinit var scenario: ActivityScenario<MapaHuertasActivity>

    @Before
    fun setup() {
        // Launch the home activity directly for a stable start.
        scenario = ActivityScenario.launch(MapaHuertasActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun openCreateHuertaFlow_showsDatosHuerta() {
        // Launch SeleccionarPerfilActivity, click first profile and expect AjustarRangosActivity to appear
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = SeleccionarPerfilActivity.crearIntent(ctx)
        val selScenario = ActivityScenario.launch<SeleccionarPerfilActivity>(intent)

        onView(withId(R.id.rvPerfiles)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click())
        )

        // Expect AjustarRangosActivity shows the profile name view
        onView(withId(R.id.tvPerfilNombre)).check(matches(isDisplayed()))
        selScenario.close()
    }

    @Test
    fun openSeleccionarPerfil_showsProfilesList() {
        // Start SeleccionarPerfilActivity directly and check RecyclerView is displayed
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = SeleccionarPerfilActivity.crearIntent(ctx)
        val selScenario = ActivityScenario.launch<SeleccionarPerfilActivity>(intent)
        onView(withId(R.id.rvPerfiles)).check(matches(isDisplayed()))
        selScenario.close()
    }
}
