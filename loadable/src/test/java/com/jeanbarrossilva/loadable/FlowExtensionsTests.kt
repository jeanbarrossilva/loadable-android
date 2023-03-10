package com.jeanbarrossilva.loadable

import app.cash.turbine.test
import com.jeanbarrossilva.loadable.utils.filterIsFailed
import com.jeanbarrossilva.loadable.utils.filterIsLoaded
import com.jeanbarrossilva.loadable.utils.innerMap
import com.jeanbarrossilva.loadable.utils.loadable
import com.jeanbarrossilva.loadable.utils.loadableChannelFlow
import com.jeanbarrossilva.loadable.utils.send
import com.jeanbarrossilva.loadable.utils.unwrap
import java.io.Serializable
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals

internal class FlowExtensionsTests {
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable Flow built through a scope WHEN loading THEN a Loading has been emitted`() {
        runTest {
            loadable<Serializable?> { load() }.test {
                repeat(2) { assertIs<Loadable.Loading<Serializable?>>(awaitItem()) }
                awaitComplete()
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable Flow built through a scope WHEN loaded THEN a Loaded has been emitted`() {
        runTest {
            loadable<Serializable?> { load(null) }.unwrap().test {
                assertEquals(null, awaitItem())
                awaitComplete()
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable Flow built through a scope WHEN failed THEN a Failed has been emitted`() {
        runTest {
            loadable<Serializable?> { fail(NullPointerException()) }.filterIsFailed().test {
                awaitItem()
                awaitComplete()
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable Flow WHEN collecting it THEN the value that's emitted first is a Loading one`() {
        runTest {
            loadable<Serializable>().test {
                assertIs<Loadable.Loading<Serializable>>(awaitItem())
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable Flow WHEN filtering Failed values THEN they're all emitted`() {
        runTest {
            flow {
                emit(9)
                null!!
            }
                .loadable()
                .filterIsFailed()
                .test {
                    assertIs<NullPointerException>(awaitItem().error)
                    awaitComplete()
                }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable Flow WHEN filtering Loaded values THEN they're all emitted`() {
        runTest {
            loadable<String> {
                load("Getting to it...")
                load()
                fail(Throwable())
                load("Failed. Trying again...")
                load("Almost there...")
                load("Done!")
            }
                .filterIsLoaded()
                .test {
                    repeat(4) { awaitItem() }
                    awaitComplete()
                }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable Flow WHEN inner-mapping it THEN the emitted values are transformed`() {
        runTest {
            loadable<Int> {
                load(1)
                load(2)
                load(3)
            }
                .innerMap(2::times)
                .unwrap()
                .test {
                    assertEquals(2, awaitItem())
                    assertEquals(4, awaitItem())
                    assertEquals(6, awaitItem())
                    awaitComplete()
                }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Flow WHEN converting it into a Loadable one THEN the value that's emitted first is a Loaded one`() {
        runTest {
            flow { emit(true) }.loadable().test {
                assertIs<Loadable.Loading<Boolean>>(awaitItem())
                awaitItem()
                awaitComplete()
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Flow WHEN converting it into a Loadable one THEN its values are emitted as Loaded`() {
        runTest {
            flow {
                emit("Hello, ")
                emit("world")
                emit("!")
            }
                .loadable()
                .test {
                    awaitItem()
                    repeat(3) { assertIs<Loadable.Loaded<String>>(awaitItem()) }
                    awaitComplete()
                }
        }
    }

    @Test
    @Suppress("DIVISION_BY_ZERO")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Flow WHEN converting it into a Loadable one THEN thrown exceptions are emitted as Failed`() {
        runTest {
            flow<Serializable> { 0 / 0 }
                .loadable()
                .filterIsFailed()
                .test {
                    assertIs<ArithmeticException>(awaitItem().error)
                    awaitComplete()
                }
        }
    }

    @Test
    @Suppress("SpellCheckingInspection", "KotlinConstantConditions")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable Flow WHEN unwrapping it THEN only Loaded Loadables' values are emitted`() {
        runTest {
            loadable<Int> {
                load(8)
                fail(Throwable())
                load()
                load(16)
            }
                .unwrap()
                .test {
                    assertEquals(8, awaitItem())
                    assertEquals(16, awaitItem())
                    awaitComplete()
                }
        }
    }

    @Test
    @Suppress("CAST_NEVER_SUCCEEDS")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `GIVEN a Loadable ChannelFlow WHEN emitting from different scopes THEN it receives sent emissions`() {
        runTest {
            loadableChannelFlow {
                send(2)
                send(4)
                send(Loadable.Failed(Throwable()))
                send(Loadable.Loading())
                send(8)
                "String" as Int
            }
                .test {
                    assertIs<Loadable.Loading<Int>>(awaitItem())
                    assertEquals(Loadable.Loaded(2), awaitItem())
                    assertEquals(Loadable.Loaded(4), awaitItem())
                    assertIs<Loadable.Failed<Int>>(awaitItem())
                    assertIs<Loadable.Loading<Int>>(awaitItem())
                    assertEquals(Loadable.Loaded(8), awaitItem())
                    awaitItem().let {
                        assertIs<Loadable.Failed<Int>>(it)
                        assertIs<ClassCastException>(it.error)
                    }
                    awaitComplete()
                }
        }
    }
}
