package com.ssaka.softplayer

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import com.google.common.util.concurrent.MoreExecutors

data class Song(val id:Long,val title:String,val artist:String,val uri:Uri,val duration:Long,val fileName:String)

class MainActivity:ComponentActivity(){
 private var songs by mutableStateOf<List<Song>>(emptyList())
 private var allowed by mutableStateOf(false)
 private var controller by mutableStateOf<MediaController?>(null)
 private var future:com.google.common.util.concurrent.ListenableFuture<MediaController>?=null
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ok->allowed=ok;if(ok)scan()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);allowed=hasPermission();if(allowed)scan()
  future=MediaController.Builder(this,SessionToken(this,ComponentName(this,SoftPlayerPlaybackService::class.java))).buildAsync()
  future?.addListener({try{controller=future?.get()}catch(_:Exception){}},MoreExecutors.directExecutor())
  setContent{Theme{Screen(songs,allowed,controller){permission.launch(audioPermission())}}}
 }
 private fun audioPermission()=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
 private fun hasPermission()=ContextCompat.checkSelfPermission(this,audioPermission())==PackageManager.PERMISSION_GRANTED
 private fun scan(){val u=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;val cols=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DISPLAY_NAME)
  contentResolver.query(u,cols,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,MediaStore.Audio.Media.TITLE+" ASC")?.use{c->
   val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val t=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val a=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val d=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);val n=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);val out=mutableListOf<Song>()
   while(c.moveToNext()){val x=c.getLong(id);val name=c.getString(n)?: "";out.add(Song(x,c.getString(t)?:name,c.getString(a)?.takeIf{it!="<unknown>"}?:"Unknown artist",ContentUris.withAppendedId(u,x),c.getLong(d),name))};songs=out
  }
 }
 override fun onDestroy(){future?.let{MediaController.releaseFuture(it)};super.onDestroy()}
}

class SoftPlayerPlaybackService:androidx.media3.session.MediaSessionService(){
 private var session:androidx.media3.session.MediaSession?=null
 override fun onCreate(){super.onCreate();val p=androidx.media3.exoplayer.ExoPlayer.Builder(this).build();session=androidx.media3.session.MediaSession.Builder(this,p).build()}
 override fun onGetSession(controllerInfo:androidx.media3.session.MediaSession.ControllerInfo)=session
 override fun onDestroy(){session?.run{player.release();release()};session=null;super.onDestroy()}
}
@Composable private fun Theme(content:@Composable()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFC56BFF),background=Color(0xFF0B0B12),surface=Color(0xFF191624)),content=content)}
private fun fmt(ms:Long):String{val s=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(s/60,s%60)}
private fun type(song:Song)=when(song.fileName.substringAfterLast('.', "").lowercase()){"mp3"->"MP3";"wav"->"WAV";"aac","m4a"->"AAC";"ogg","oga"->"OGG";"flac"->"FLAC";"wma"->"WMA";else->"OTHER"}

@Composable private fun Screen(songs:List<Song>,allowed:Boolean,p:Player?,ask:()->Unit){
 var page by remember{mutableStateOf("Library")};var filter by remember{mutableStateOf("ALL")};var playing by remember{mutableStateOf(false)};var index by remember{mutableIntStateOf(-1)};var pos by remember{mutableLongStateOf(0)};var dur by remember{mutableLongStateOf(0)}
 val current=songs.getOrNull(index)
 LaunchedEffect(p){while(true){if(p!=null){playing=p.isPlaying;index=p.currentMediaItemIndex.takeIf{it in songs.indices}?:index;pos=p.currentPosition.coerceAtLeast(0);dur=p.duration.coerceAtLeast(0)};delay(350)}}
 fun play(i:Int){if(p==null||i !in songs.indices)return;index=i;p.setMediaItems(songs.map{MediaItem.fromUri(it.uri)},i,0);p.prepare();p.play();page="Now Playing"}
 fun next(){if(p==null)return;if(p.hasNextMediaItem())p.seekToNext()else if(songs.isNotEmpty())play(0)}
 fun prev(){if(p==null)return;if(p.currentPosition>3000)p.seekTo(0)else if(p.hasPreviousMediaItem())p.seekToPrevious()else if(songs.isNotEmpty())play(songs.lastIndex)}
 val visible=songs.filter{filter=="ALL"||type(it)==filter}
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal=16.dp)){
  Spacer(Modifier.height(18.dp));Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Color(0xFF7130B6),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Headphones,null)};Spacer(Modifier.width(10.dp));Column{Text("SOFT PLAYER",fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("Your music. Your mood.",fontSize=12.sp,color=Color.Gray)};Spacer(Modifier.weight(1f));Icon(Icons.Default.GraphicEq,null,tint=MaterialTheme.colorScheme.primary)}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=12.dp)){FilterChip(page=="Library",{page="Library"},label={Text("Library")});FilterChip(page=="Now Playing",{page="Now Playing"},label={Text("Now Playing")})}
  if(page=="Library"){
   Text("${songs.size} songs",fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth()){listOf("ALL","MP3","WAV","AAC","OGG","FLAC","OTHER").forEach{c->FilterChip(filter==c,{filter=c},label={Text(c,fontSize=10.sp)},modifier=Modifier.weight(1f))}}
   if(!allowed){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.Center){Text("Allow audio access to scan your music");Button(onClick=ask){Text("Grant access")}}}}
   else if(visible.isEmpty()){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(songs.isEmpty())"No music found" else "No $filter files",color=Color.Gray)}}
   else{LazyColumn(Modifier.weight(1f)){items(visible,key={it.id}){song->Row(Modifier.fillMaxWidth().clickable{play(songs.indexOf(song))}.padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(Color(0xFF211A30),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.MusicNote,null,tint=MaterialTheme.colorScheme.primary)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text("${song.artist} • ${type(song)}",fontSize=12.sp,color=Color.Gray,maxLines=1)};Text(fmt(song.duration),fontSize=12.sp,color=Color.Gray)};HorizontalDivider(color=Color(0xFF292536))}}}
  }else{
   if(current==null){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text("Choose a song from Library",color=Color.Gray)}}
   else{Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.Center,verticalArrangement=Arrangement.Center){Disc(playing);Spacer(Modifier.height(24.dp));Text(current.title,fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(current.artist,color=Color.Gray);Text(type(current),fontSize=12.sp,color=MaterialTheme.colorScheme.primary);Slider(value=if(dur>0)pos.toFloat().coerceIn(0f,dur.toFloat())else 0f,onValueChange={pos=it.toLong()},onValueChangeFinished={p?.seekTo(pos)},valueRange=0f..dur.coerceAtLeast(1).toFloat(),modifier=Modifier.padding(top=20.dp));Row{Text(fmt(pos),fontSize=12.sp,color=Color.Gray);Spacer(Modifier.weight(1f));Text(fmt(dur),fontSize=12.sp,color=Color.Gray)}
    Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){IconButton(onClick={prev()}){Icon(Icons.Default.SkipPrevious,null,Modifier.size(38.dp))};IconButton(onClick={if(playing)p?.pause()else p?.play()},Modifier.size(58.dp).background(Color(0xFF7D28C8),CircleShape)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,Modifier.size(32.dp))};IconButton(onClick={next()}){Icon(Icons.Default.SkipNext,null,Modifier.size(38.dp))};IconButton(onClick={p?.stop();index=-1;pos=0;page="Library"}){Icon(Icons.Default.Stop,null,Modifier.size(34.dp))}}
    Text("Background playback enabled",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=18.dp))}}
  }
  if(current!=null&&page=="Library"){Row(Modifier.fillMaxWidth().background(Color(0xFF191624),RoundedCornerShape(15.dp)).clickable{page="Now Playing"}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Album,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(current.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold);Text(current.artist,fontSize=11.sp,color=Color.Gray)};IconButton(onClick={if(playing)p?.pause()else p?.play()}){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null)};IconButton(onClick={next}){Icon(Icons.Default.SkipNext,null)}}
  }
  Spacer(Modifier.height(8.dp))
 }
}
@Composable private fun Disc(playing:Boolean){val tr=rememberInfiniteTransition(label="disc");val angle by tr.animateFloat(0f,360f,infiniteRepeatable(tween(5000,easing=LinearEasing),RepeatMode.Restart),label="rotation");Box(Modifier.size(248.dp).rotate(if(playing)angle else 0f).background(Color(0xFF24212D),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(218.dp).background(Color(0xFF111117),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(178.dp).background(Color(0xFF292431),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(112.dp).background(Color(0xFF7D28C8),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Headphones,null,Modifier.size(52.dp))}};Box(Modifier.size(8.dp).background(Color.Black,CircleShape))}}
