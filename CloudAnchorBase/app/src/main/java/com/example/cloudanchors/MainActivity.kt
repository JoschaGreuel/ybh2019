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
import com.google.ar.sceneform.math.Vector3
import android.widget.Toast
import com.google.ar.sceneform.rendering.ViewRenderable
import android.content.Intent
import android.view.View


class MainActivity : AppCompatActivity() {

    lateinit var arFragment: CloudAnchorFragment
    var cloudAnchor: Anchor? = null

    // global renderable
    var modelRenderable: ModelRenderable? = null

    // global view renderable (buttons)
    var pickupSelectionRenderable : ViewRenderable? = null

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

        btn_menu.setVisibility(View.INVISIBLE)

        //Create the beer renderable
        ModelRenderable.builder()
            //get the context of the ARFragment and pass the name of your .sfb file
            .setSource(arFragment.context, Uri.parse("model.sfb"))
            .build()

            //I accepted the CompletableFuture using Async since I created my model on creation of the activity. You could simply use .thenAccept too.
            //Use the returned modelRenderable and save it to a global variable of the same name
            .thenAcceptAsync { modelRenderable -> this@MainActivity.modelRenderable = modelRenderable }

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
        btn_menu.setOnClickListener{
            val intent1 = Intent(this, OrderActivity::class.java)
            intent1.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP //
            this.startActivity(intent1)
            finish()
        }

        btn_drawBeer.setOnClickListener{
            Toast.makeText(applicationContext, "trying to draw beer", Toast.LENGTH_SHORT).show()

            // snackbarHelper.showMessage(this, "O zapf isch!")

            //create a new TranformableNode that will carry our object
            val transformableNode = TransformableNode(arFragment.transformationSystem)

            if(this@MainActivity.cloudAnchor != null) {
                Toast.makeText(applicationContext, "Cloud anchor available. Setting transformableNode", Toast.LENGTH_SHORT).show()

                val anchorNode = AnchorNode(this@MainActivity.cloudAnchor)
                transformableNode.setParent(anchorNode)
                transformableNode.renderable = this@MainActivity.modelRenderable
                transformableNode.scaleController.isEnabled = true

                arFragment.arSceneView.scene.addChild(anchorNode)

                //Alter the real world position
                transformableNode.worldPosition = Vector3(0f, 0f, 0f)
                transformableNode.select() // Sets this as the selected node in the TransformationSystem if there is no currently selected node or if the currently selected node is not actively being transformed.

                transformableNode.setOnTapListener { hitTestResult, motionEvent ->
                    Toast.makeText(applicationContext, "Bonsai tapped, please make a choice", Toast.LENGTH_SHORT).show()

                    if(this@MainActivity.pickupSelectionRenderable == null) {
                        ViewRenderable.builder().setView(this, R.layout.pickup_selection).build()
                            .thenAccept { renderable ->
                                this@MainActivity.pickupSelectionRenderable = renderable
                            };
                    }

                    val buttonTransformable  = TransformableNode(arFragment.transformationSystem)
                    buttonTransformable.renderable = this@MainActivity.pickupSelectionRenderable
                    buttonTransformable.setParent(anchorNode)
                    transformableNode.worldPosition = Vector3(0f, 0f, 0f)

                }


                snackbarHelper.showMessageWithDismiss(this, "anchorNode is at " + anchorNode.worldPosition.toString() + "\nbonsai is at " + transformableNode.worldPosition.toString())


            } else {
                Toast.makeText(applicationContext, "Error: Cloud anchor not available", Toast.LENGTH_SHORT).show()
            }
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
            //btn_menu.getBackground().setAlpha(0);
            return

        //btn_menu.setVisibility(View.VISIBLE)
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
                //btn_menu.getBackground().setAlpha(255);
                //btn_menu.currentTextColor().setAlpha(255);
                //btn_menu.setVisibility(View.VISIBLE)
                btn_menu.setVisibility(View.VISIBLE)
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
