package com.example.cloudanchors

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import com.google.ar.core.Anchor.CloudAnchorState
import android.content.ClipboardManager
import android.R.attr.label
import android.content.ClipData
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import androidx.core.app.ComponentActivity
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T



class MainActivity : AppCompatActivity() {

    lateinit var arFragment: CloudAnchorFragment
    var cloudAnchor: Anchor? = null

    enum class AppAnchorState {
        NONE,
        HOSTING,
        HOSTED,
        RESOLVING,
        RESOLVED
    }

    var appAnchorState = AppAnchorState.NONE
    var snackbarHelper = SnackbarHelper()
    var storageManager = StorageManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as CloudAnchorFragment
        arFragment.arSceneView.scene.addOnUpdateListener(this::onUpdateFrame)
        arFragment.planeDiscoveryController.hide()
        arFragment.planeDiscoveryController.setInstructionView(null)

        btn_clear.setOnClickListener {
            cloudAnchor(null)
        }

        btn_resolve.setOnClickListener {
            if (cloudAnchor != null) {
                snackbarHelper.showMessageWithDismiss(this, "Please clear the anchor")
                return@setOnClickListener
            }

            val dialog = ResolveDialogFragment()
            dialog.setOkListener(this::onResolveOkPressed)
            dialog.show(supportFragmentManager, "Resolve")
        }

        arFragment.setOnTapArPlaneListener { hitResult, plane, _ ->
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING || appAnchorState != AppAnchorState.NONE) {
                return@setOnTapArPlaneListener
            }

            val anchor = arFragment.arSceneView.session?.hostCloudAnchor(hitResult.createAnchor())
            cloudAnchor(anchor)

            appAnchorState = AppAnchorState.HOSTING
            snackbarHelper.showMessage(this, "Hosting anchor")

            placeObject(arFragment, cloudAnchor!!, Uri.parse("GlassOfBeer.sfb"))

        }

    }

    fun onResolveOkPressed(dialogVal: String) {
        //val shortCode = dialogVal.toInt()
        val cloudAnchorId = dialogVal
        //val cloudAnchorId = storageManager.getCloudAnchorID(this, shortCode)
        val resolvedAnchor = arFragment.arSceneView.session?.resolveCloudAnchor(cloudAnchorId)
        cloudAnchor(resolvedAnchor)
        placeObject(arFragment, cloudAnchor!!, Uri.parse("GlassOfBeer.sfb"))
        snackbarHelper.showMessage(this, "Now resolving anchor...")
        appAnchorState = AppAnchorState.RESOLVING
    }

    fun onUpdateFrame(frameTime: FrameTime) {
        checkUpdatedAnchor()
    }

    @Synchronized
    private fun checkUpdatedAnchor() {
        if (appAnchorState != AppAnchorState.HOSTING && appAnchorState != AppAnchorState.RESOLVING)
            return

        val cloudState: CloudAnchorState = cloudAnchor?.cloudAnchorState!!

        if (appAnchorState == AppAnchorState.HOSTING) {
            if (cloudState.isError) {
                snackbarHelper.showMessageWithDismiss(this, "Error hosting anchor...")
                appAnchorState = AppAnchorState.NONE
            } else if (cloudState == CloudAnchorState.SUCCESS) {
                //val shortCode = storageManager.getTime();//storageManager.nextShortCode(this)
                //storageManager.storeUsingShortCode(this, shortCode, cloudAnchor?.cloudAnchorId)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ID", cloudAnchor?.cloudAnchorId)
                clipboard.setPrimaryClip(clip)
                snackbarHelper.showMessageWithDismiss(this, "Anchor hosted, key in Clipboard.")// $shortCode")
                appAnchorState = AppAnchorState.HOSTED
            }
        } else if (appAnchorState == AppAnchorState.RESOLVING) {
            if (cloudState.isError) {
                snackbarHelper.showMessageWithDismiss(this, "Error resolving anchor...")
                appAnchorState = AppAnchorState.NONE
            } else if (cloudState == CloudAnchorState.SUCCESS) {
                snackbarHelper.showMessageWithDismiss(this, "Anchor resolved...")
                appAnchorState = AppAnchorState.RESOLVED
            }
        }

    }

    private fun cloudAnchor(newAnchor: Anchor?) {
        cloudAnchor?.detach()
        cloudAnchor = newAnchor
        appAnchorState = AppAnchorState.NONE
        snackbarHelper.hide(this)
    }

    private fun placeObject(fragment: ArFragment, anchor: Anchor, model: Uri) {
        ModelRenderable.Builder()
            .setSource(fragment.context, model)
            .build()
            .thenAccept { renderable ->
                addNodeToScene(fragment, anchor, renderable)
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error!")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }
    }

    private fun addNodeToScene(fragment: ArFragment, anchor: Anchor, renderable: ModelRenderable) {
        val node = AnchorNode(anchor)
        val transformableNode = TransformableNode(fragment.transformationSystem)
        transformableNode.renderable = renderable
        transformableNode.setParent(node)
        fragment.arSceneView.scene.addChild(node)
        transformableNode.select()
    }

}
