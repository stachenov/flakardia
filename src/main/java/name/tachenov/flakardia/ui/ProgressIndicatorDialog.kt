package name.tachenov.flakardia.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import name.tachenov.flakardia.ProgressIndicator
import name.tachenov.flakardia.UiProgressIndicator
import name.tachenov.flakardia.assertEDT
import name.tachenov.flakardia.presenter.Presenter
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar

fun dialogIndicator(owner: Presenter<*, *>, job: Job): ProgressIndicator {
    return object : UiProgressIndicator(job) {
        private var dialog: ProgressIndicatorDialog? = null

        override fun publishCurrentOperation(currentOperation: String?) {
            dialog().operationLabel.text = currentOperation
        }

        override fun publishProgress(progress: Int) {
            dialog().progressBar.value = progress
        }

        override fun close() {
            dialog?.dispose()
        }

        private fun dialog(): ProgressIndicatorDialog {
            assertEDT()
            job.ensureActive()
            var dialog = dialog
            if (dialog == null) {
                dialog = ProgressIndicatorDialog()
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
                dialog.addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent?) {
                        job.cancel()
                    }
                })
                this.dialog = dialog
            }
            if (System.currentTimeMillis() - dialog.created >= 300L) {
                dialog.pack()
                dialog.setLocationRelativeTo(owner)
                dialog.isVisible = true
            }
            return dialog
        }
    }
}

private class ProgressIndicatorDialog : JDialog() {
    val operationLabel = JLabel()
    val progressBar = JProgressBar()
    val created = System.currentTimeMillis()

    init {
        val content = JPanel(BorderLayout())
        content.add(operationLabel, BorderLayout.NORTH)
        content.add(progressBar, BorderLayout.CENTER)
        progressBar.preferredSize = Dimension(600, 25)
        contentPane = content
    }
}
