/*
 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public
 License as published by the Free Software Foundation; either
 version 2 of the license, or (at your option) any later version.
 */

package org.gjt.jclasslib.browser

import com.install4j.runtime.alert.AlertType
import kotlinx.dom.build.addElement
import org.gjt.jclasslib.browser.BrowserBundle.getString
import org.gjt.jclasslib.browser.config.BrowserPath
import org.gjt.jclasslib.browser.config.classpath.ClasspathEntry
import org.gjt.jclasslib.browser.config.classpath.FindResult
import org.gjt.jclasslib.io.ClassFileWriter
import org.gjt.jclasslib.structures.ClassFile
import org.gjt.jclasslib.util.GUIHelper
import org.w3c.dom.Element
import java.awt.BorderLayout
import java.io.File
import java.io.IOException
import javax.swing.Action
import javax.swing.JPanel
import javax.swing.SwingUtilities

class BrowserTab(val fileName: String, val moduleName: String, frame: BrowserFrame) : JPanel(), BrowserServices {

    private val tabbedPane: BrowserTabbedPane
        get() = SwingUtilities.getAncestorOfClass(BrowserTabbedPane::class.java, this) as BrowserTabbedPane

    private val frameContent: FrameContent
        get() = tabbedPane.container

    private val parentFrame: BrowserFrame
        get() = frameContent.frame

    override var classFile: ClassFile = readClassFile(fileName, frame)
    override val browserComponent: BrowserComponent = BrowserComponent(this)
    override var isModified: Boolean = false

    override fun activate() {
        tabbedPane.focus()
        // force sync of toolbar state with this tab
    }

    override val backwardAction: Action
        get() = parentFrame.backwardAction

    override val forwardAction: Action
        get() = parentFrame.forwardAction

    override fun openClassFile(className: String, browserPath: BrowserPath?) {
        val findResult: FindResult? = findClass(className)
        if (findResult != null) {
            val openTab = frameContent.findTab(findResult.fileName)
            if (openTab != null) {
                openTab.apply {
                    select()
                    browserComponent.browserPath = browserPath
                }
            } else {
                try {
                    tabbedPane.addTab(findResult.fileName, findResult.moduleName, browserPath)
                } catch (e: IOException) {
                    GUIHelper.showMessage(parentFrame, e)
                }

            }
        }
    }

    fun saveClassToDirectory(directory: File) : Boolean {
        val simpleClassName = classFile.simpleClassName
        val packageDirectory = File(directory, classFile.thisClassName.removeSuffix(simpleClassName))
        packageDirectory.mkdirs()
        val file = File(packageDirectory, "$simpleClassName.class")
        return try {
            ClassFileWriter.writeToFile(file, classFile)
            true
        } catch (e: Exception) {
            GUIHelper.showMessage(parentFrame, getString("message.class.save.error", file.path), getString("message.error.message", e.message ?: ""), AlertType.ERROR)
            false
        }
    }

    private tailrec fun findClass(className: String): FindResult? = parentFrame.config.findClass(className, false) ?:
            if (GUIHelper.showOptionDialog(parentFrame,
                    getString("message.class.not.found.title"),
                    getString("message.class.not.found", className),
                    arrayOf(getString("action.setup.class.path"), getString("action.cancel")),
                    AlertType.WARNING) != 0) {
                null
            } else {
                parentFrame.setupClasspathAction()
                findClass(className)
            }

    init {
        layout = BorderLayout()
        add(browserComponent, BorderLayout.CENTER)
    }

    fun reload() {
        if (tabbedPane.canRemove(this)) {
            resetModified()
            classFile = readClassFile(fileName, parentFrame)
            browserComponent.rebuild()
        }
    }

    private fun select() {
        tabbedPane.selectedComponent = this
        tabbedPane.focus()
        browserComponent.treePane.tree.requestFocus()
    }

    override fun canOpenClassFiles(): Boolean = true
    override fun canSaveClassFiles(): Boolean = true

    override fun showURL(urlSpec: String) {
        GUIHelper.showURL(urlSpec)
    }

    override fun modified() {
        isModified = true
        tabbedPane.updateSelectedTitle()
        frameContent.updateSaveAction()
    }

    fun getTabTitle(): String =
            (if (isModified) "* " else "") +
            if (moduleName != ClasspathEntry.UNNAMED_MODULE) {
                "$moduleName/"
            } else {
                ""
            } + browserComponent.title

    fun setBrowserPath(browserPath: BrowserPath?) {
        browserComponent.browserPath = browserPath
    }

    fun saveWorkspace(element: Element) {
        element.addElement(NODE_NAME) {
            setAttribute(ATTRIBUTE_FILE_NAME, fileName)
            setAttribute(ATTRIBUTE_MODULE_NAME, moduleName)
            browserComponent.browserPath?.saveWorkspace(this)
        }
    }

    fun saveModified() {
        if (isModified) {
            if (writeClassFile(classFile, fileName, parentFrame)) {
                resetModified()
            }
        }
    }

    private fun resetModified() {
        isModified = false
        tabbedPane.updateTitleOf(this)
    }

    companion object {
        const val NODE_NAME = "tab"
        private const val ATTRIBUTE_FILE_NAME = "fileName"
        private const val ATTRIBUTE_MODULE_NAME = "moduleName"

        fun create(element: Element, frame: BrowserFrame): BrowserTab =
                BrowserTab(element.getAttribute(ATTRIBUTE_FILE_NAME), element.getAttributeNode(ATTRIBUTE_MODULE_NAME)?.value ?: ClasspathEntry.UNNAMED_MODULE, frame)
    }
}
