package com.example.headshaker

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory

class ArGameActivity : AppCompatActivity(), CustomArFragment.OnSceneReadyListener {

    private lateinit var arFragment: CustomArFragment
    private var bolaNode: Node? = null
    private lateinit var scoreTextView: TextView
    private lateinit var gameOverLayout: FrameLayout
    private lateinit var pauseLayout: FrameLayout

    private val bloques = mutableListOf<Node>()
    private var lastBlockSpawnTime = 0L
    private var blockMaterial: Material? = null
    private var fallSpeed = 0.09f
    private var spawnInterval = 3000L
    private val gameDistance = 0.7f
    private var score = 0
    private var isGameOver = false

    private val animatingBlocks = mutableMapOf<Node, Long>()
    private val animationDuration = 200L

    private lateinit var soundPool: SoundPool
    private var popSoundId: Int = 0
    private var gameOverSoundId: Int = 0
    private var mediaPlayer: MediaPlayer? = null
    private var isMusicMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_game)

        isMusicMuted = intent.getBooleanExtra("isMusicMuted", false)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as CustomArFragment
        scoreTextView = findViewById(R.id.score_text)
        gameOverLayout = findViewById(R.id.game_over_layout)
        pauseLayout = findViewById(R.id.pause_layout)

        arFragment.setOnSceneReadyListener(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_GAME).build()
        soundPool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(audioAttributes).build()
        popSoundId = soundPool.load(this, R.raw.pop, 1)
        gameOverSoundId = soundPool.load(this, R.raw.gameover, 1)

        if (!isMusicMuted) {
            mediaPlayer = MediaPlayer.create(this, R.raw.gamemusic)
            mediaPlayer?.isLooping = true
        }
    }

    override fun onSceneReady() {
        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(android.graphics.Color.RED))
            .thenAccept { material ->
                val sphereRenderable = ShapeFactory.makeSphere(0.02f, Vector3.zero(), material)
                bolaNode = Node().apply { renderable = sphereRenderable; isEnabled = false }
                arFragment.arSceneView.scene.addChild(bolaNode)
            }

        MaterialFactory.makeTransparentWithColor(this, com.google.ar.sceneform.rendering.Color(android.graphics.Color.BLUE))
            .thenAccept { material -> blockMaterial = material }

        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            if (isGameOver || bolaNode == null || blockMaterial == null) return@addOnUpdateListener

            val faces = arFragment.arSceneView.session?.getAllTrackables(AugmentedFace::class.java)
            val firstTrackedFace = faces?.firstOrNull { it.trackingState == TrackingState.TRACKING }
            val now = System.currentTimeMillis()

            if (firstTrackedFace != null) {
                pauseLayout.visibility = View.GONE
                if (!isMusicMuted && mediaPlayer?.isPlaying == false) mediaPlayer?.start()

                bolaNode!!.isEnabled = true
                bloques.forEach { it.isEnabled = true }

                val camera = arFragment.arSceneView.scene.camera
                val nosePose = firstTrackedFace.getRegionPose(AugmentedFace.RegionType.NOSE_TIP)
                val noseWorldPos = Vector3(nosePose.tx(), nosePose.ty(), nosePose.tz())
                val noseScreenPos = camera.worldToScreenPoint(noseWorldPos)
                val bolaRay = camera.screenPointToRay(noseScreenPos.x, noseScreenPos.y)
                bolaNode!!.worldPosition = bolaRay.getPoint(gameDistance)

                val distanceToCamera = Vector3.subtract(camera.worldPosition, bolaNode!!.worldPosition).length()
                bolaNode!!.localScale = Vector3(distanceToCamera, distanceToCamera, distanceToCamera)

                val view = arFragment.arSceneView
                if (now - lastBlockSpawnTime > spawnInterval) {
                    lastBlockSpawnTime = now
                    spawnNewBlock(camera, view.width.toFloat())
                }

                updateBlocks(frameTime.deltaSeconds, view.height.toFloat(), camera)
            } else {
                if (!isGameOver) pauseLayout.visibility = View.VISIBLE
                if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
                bolaNode!!.isEnabled = false
                bloques.forEach { it.isEnabled = false }
            }
            handleBlockAnimation(now)
        }
    }

    private fun spawnNewBlock(camera: com.google.ar.sceneform.Camera, screenWidth: Float) {
        val randomScreenX = (screenWidth / 2f) + (Math.random().toFloat() * 800f - 400f)
        val spawnRay = camera.screenPointToRay(randomScreenX, 0f)
        val block = Node().apply {
            renderable = ShapeFactory.makeCube(Vector3(0.06f, 0.06f, 0.06f), Vector3.zero(), blockMaterial)
            worldPosition = spawnRay.getPoint(gameDistance)
        }
        arFragment.arSceneView.scene.addChild(block)
        bloques.add(block)
    }

    private fun updateBlocks(deltaSeconds: Float, viewHeight: Float, camera: com.google.ar.sceneform.Camera) {
        val iterator = bloques.iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            block.worldPosition = Vector3(block.worldPosition.x, block.worldPosition.y - fallSpeed * deltaSeconds, block.worldPosition.z)

            val dx = block.worldPosition.x - bolaNode!!.worldPosition.x
            val dy = block.worldPosition.y - bolaNode!!.worldPosition.y
            if (dx * dx + dy * dy < 0.04f * 0.04f) {
                iterator.remove()
                soundPool.play(popSoundId, 1f, 1f, 1, 0, 1f)
                block.renderable?.material = block.renderable?.material?.makeCopy()
                animatingBlocks[block] = System.currentTimeMillis()
                score++
                scoreTextView.text = "Puntos: $score"

                if (score > 0 && score % 5 == 0) {
                    fallSpeed += 0.03f
                    if (spawnInterval > 1000L) {
                        spawnInterval -= 500L
                    } else if (spawnInterval > 500L){
                        spawnInterval -= 100L
                    }
                }
                continue
            }

            if (camera.worldToScreenPoint(block.worldPosition).y >= viewHeight) {
                showGameOver()
                return
            }
        }
    }

    private fun showGameOver() {
        isGameOver = true
        mediaPlayer?.stop()
        soundPool.play(gameOverSoundId, 1f, 1f, 1, 0, 1f)
        gameOverLayout.visibility = View.VISIBLE

        bolaNode?.isEnabled = false
        bloques.forEach { it.isEnabled = false }
        animatingBlocks.keys.forEach { it.isEnabled = false }

        val resultIntent = Intent()
        resultIntent.putExtra("score", score)
        setResult(RESULT_OK, resultIntent)

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3000)
    }

    private fun handleBlockAnimation(now: Long) {
        val animIterator = animatingBlocks.iterator()
        while (animIterator.hasNext()) {
            val (block, startTime) = animIterator.next()
            val elapsedTime = now - startTime
            if (elapsedTime >= animationDuration) {
                arFragment.arSceneView.scene.removeChild(block)
                animIterator.remove()
            } else {
                val progress = elapsedTime.toFloat() / animationDuration
                val scale = 1.0f + 0.3f * progress
                block.localScale = Vector3(scale, scale, scale)
                block.renderable?.material?.setFloat4("color", 0f, 0f, 1f, 1.0f - progress)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        soundPool.autoPause()
    }

    override fun onResume() {
        super.onResume()
        if (!isGameOver) soundPool.autoResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
