package com.ssaka.softplayer
import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay

private val BG=Color(0xFF090910); private val CARD=Color(0xFF171522); private val CARD2=Color(0xFF262033)
private val PURPLE=Color(0xFFB96BFF); private val ACCENT=Color(0xFF852DD3); private val WHITE=Color(0xFFF5F1FB); private val MUTED=Color(0xFFAAA5B8)
data class Song(val id:Long,val title:String,val artist:String,val uri:Uri,val duration:Long,val filename:String)
class MainActivity:ComponentActivity(){
 private var songs by mutableStateOf<List<Song>>(emptyList()); private var allowed by mutableStateOf(false); private var controller by mutableStateOf<MediaController?>(null)
 private var future:com.google.common.util.concurrent.ListenableFuture<MediaController>?=null
 private val perm=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
 private val request=registerForActivityResult(ActivityResultContracts.RequestPermission()){allowed=it;if(it)scan()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);allowed=ContextCompat.checkSelfPermission(this,perm)==PackageManager.PERMISSION_GRANTED;if(allowed)scan()
 future=MediaController.Builder(this,SessionToken(this,ComponentName(this,SoftPlayerPlaybackService::class.java))).buildAsync()
 future?.addListener({try{controller=future?.get()}catch(_:Exception){}},MoreExecutors.directExecutor())
 setContent { AppTheme(content = { PlayerUI(songs, allowed, controller, ask = { request.launch(perm) }) }) }
 private fun scan(){val u=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;val cols=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DISPLAY_NAME)
 contentResolver.query(u,cols,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,MediaStore.Audio.Media.TITLE+" ASC")?.use{c->val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val t=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val a=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val d=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);val n=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);val l=mutableListOf<Song>();while(c.moveToNext()){val x=c.getLong(id);val file=c.getString(n)?: "";l.add(Song(x,c.getString(t)?:file,c.getString(a)?.takeIf{it!="<unknown>"}?:"Unknown artist",ContentUris.withAppendedId(u,x),c.getLong(d),file))};songs=l}}
 override fun onDestroy(){future?.let{MediaController.releaseFuture(it)};super.onDestroy()}
}
class SoftPlayerPlaybackService:androidx.media3.session.MediaSessionService(){private var session:androidx.media3.session.MediaSession?=null
 override fun onCreate(){super.onCreate();val exo=androidx.media3.exoplayer.ExoPlayer.Builder(this).build();session=androidx.media3.session.MediaSession.Builder(this,exo).build()}
 override fun onGetSession(info:androidx.media3.session.MediaSession.ControllerInfo)=session
 override fun onDestroy(){session?.let{it.player.release();it.release()};session=null;super.onDestroy()}}
@Composable private fun AppTheme(content: @Composable () -> Unit){MaterialTheme(colorScheme=darkColorScheme(primary=PURPLE,onPrimary=Color.White,background=BG,onBackground=WHITE,surface=CARD,onSurface=WHITE,secondary=ACCENT),content=content)}
private fun fmt(s:Song)=when(s.filename.substringAfterLast('.', "").lowercase()){"mp3"->"MP3";"wav"->"WAV";"aac","m4a"->"AAC";"ogg","oga"->"OGG";"flac"->"FLAC";"wma"->"WMA";else->"OTHER"}
private fun time(ms:Long):String{val x=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(x/60,x%60)}
@Composable private fun PlayerUI(songs:List<Song>,allowed:Boolean,p:Player?,ask:()->Unit){
 val ctx=androidx.compose.ui.platform.LocalContext.current;var tab by remember{mutableStateOf("Library")};var filter by remember{mutableStateOf("ALL")}
 var favs by remember{mutableStateOf(ctx.getSharedPreferences("softplayer",0).getStringSet("favs",emptySet())?.toSet()?:emptySet())}
 var playing by remember{mutableStateOf(false)};var idx by remember{mutableIntStateOf(-1)};var pos by remember{mutableLongStateOf(0L)};var dur by remember{mutableLongStateOf(0L)}
 val song=songs.getOrNull(idx);LaunchedEffect(favs){ctx.getSharedPreferences("softplayer",0).edit().putStringSet("favs",favs.toSet()).apply()}
 LaunchedEffect(p){while(true){if(p!=null){playing=p.isPlaying;val n=p.currentMediaItemIndex;if(n in songs.indices)idx=n;pos=p.currentPosition.coerceAtLeast(0);dur=p.duration.coerceAtLeast(0)};delay(350)}}
 val favorite:(Song)->Unit={s->favs=if(s.id.toString() in favs)favs-s.id.toString() else favs+s.id.toString()}
 val play:(Int)->Unit={i->if(p!=null&&i in songs.indices){idx=i;p.setMediaItems(songs.map{MediaItem.fromUri(it.uri)},i,0);p.prepare();p.play();tab="Now Playing"}}
 val next:()->Unit={if(p!=null){if(p.hasNextMediaItem())p.seekToNext()else if(songs.isNotEmpty())play(0)}}
 val prev:()->Unit={if(p!=null){if(p.currentPosition>3000)p.seekTo(0)else if(p.hasPreviousMediaItem())p.seekToPrevious()else if(songs.isNotEmpty())play(songs.lastIndex)}}
 val list=songs.filter{(filter=="ALL"||fmt(it)==filter)&&(tab!="Favorites"||it.id.toString() in favs)}
 Column(Modifier.fillMaxSize().background(BG).padding(horizontal=17.dp,vertical=12.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).background(ACCENT,RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Headphones,null,tint=Color.White,modifier=Modifier.size(29.dp))};Spacer(Modifier.width(12.dp));Column{Text("SOFT PLAYER",color=WHITE,fontWeight=FontWeight.Black,letterSpacing=2.sp,fontSize=18.sp);Text("Your music. Your mood.",color=MUTED,fontSize=13.sp)};Spacer(Modifier.weight(1f));Icon(Icons.Default.GraphicEq,null,tint=PURPLE,modifier=Modifier.size(28.dp))}
  Row(Modifier.padding(top=17.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("Library","Favorites","Now Playing").forEach{n->val sel=tab==n;Surface(onClick={tab=n},color=if(sel)CARD2 else BG,shape=RoundedCornerShape(13.dp),border=if(sel)null else BorderStroke(1.dp,Color(0xFF514B5C))){Text(n,color=if(sel)PURPLE else MUTED,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(horizontal=13.dp,vertical=11.dp))}}}
  if(tab!="Now Playing"){
   Text(if(tab=="Favorites")"Your favorites" else "Your library",color=WHITE,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=17.dp))
   Text("${if(tab=="Favorites")list.size else songs.size} songs",color=MUTED,fontSize=13.sp,modifier=Modifier.padding(bottom=9.dp))
   Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("ALL","MP3","WAV","AAC","OGG","FLAC","WMA","OTHER").forEach{f->val sel=filter==f;Surface(onClick={filter=f},color=if(sel)ACCENT else CARD,shape=RoundedCornerShape(11.dp)){Text(f,color=if(sel)Color.White else MUTED,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=13.dp,vertical=9.dp))}}}
   if(!allowed){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Let Soft Player find your music",color=WHITE);Button(onClick=ask){Text("Grant audio access")}}}}
   else if(list.isEmpty()){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(tab=="Favorites")"Tap the heart on songs to save them here." else "No music found",color=MUTED)}}
   else{LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(bottom=8.dp)){items(list,key={it.id}){s->val isFav=s.id.toString() in favs;Surface(color=CARD,shape=RoundedCornerShape(15.dp)){Row(Modifier.clickable{play(songs.indexOf(s))}.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).background(CARD2,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.MusicNote,null,tint=PURPLE,modifier=Modifier.size(26.dp))};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(s.title,color=WHITE,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${s.artist} • ${fmt(s)}",color=MUTED,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)};Text(time(s.duration),color=MUTED,fontSize=12.sp);IconButton(onClick={favorite(s)}){Icon(if(isFav)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(isFav)PURPLE else MUTED)}}}}}}
  }else{
   if(song==null)Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text("Choose a song from your library",color=MUTED)}
   else Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
    Disc(playing);Spacer(Modifier.height(25.dp));Text(song.title,color=WHITE,fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(song.artist,color=MUTED,fontSize=15.sp,modifier=Modifier.padding(top=5.dp))
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=7.dp)){Text(fmt(song),color=PURPLE,fontWeight=FontWeight.Bold,fontSize=12.sp);IconButton(onClick={favorite(song)}){Icon(if(song.id.toString() in favs)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=PURPLE)}}
    Slider(value=if(dur>0)pos.toFloat().coerceIn(0f,dur.toFloat())else 0f,onValueChange={pos=it.toLong()},onValueChangeFinished={p?.seekTo(pos)},valueRange=0f..dur.coerceAtLeast(1).toFloat(),modifier=Modifier.padding(top=15.dp))
    Row(Modifier.fillMaxWidth()){Text(time(pos),color=MUTED,fontSize=12.sp);Spacer(Modifier.weight(1f));Text(time(dur),color=MUTED,fontSize=12.sp)}
    Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){IconButton(onClick=prev){Icon(Icons.Default.SkipPrevious,null,tint=WHITE,modifier=Modifier.size(35.dp))};IconButton(onClick={if(playing)p?.pause()else p?.play()},Modifier.size(66.dp).background(ACCENT,CircleShape)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.size(35.dp))};IconButton(onClick=next){Icon(Icons.Default.SkipNext,null,tint=WHITE,modifier=Modifier.size(35.dp))};IconButton(onClick={p?.stop();idx=-1;pos=0;tab="Library"}){Icon(Icons.Default.Stop,null,tint=MUTED,modifier=Modifier.size(30.dp))}}
    Text("Background playback enabled",color=MUTED,fontSize=12.sp,modifier=Modifier.padding(top=15.dp))
   }
  }
  if(song!=null&&tab!="Now Playing"){Surface(color=Color(0xFF1D1829),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(top=7.dp)){Row(Modifier.clickable{tab="Now Playing"}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(ACCENT,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Album,null,tint=Color.White)};Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(song.title,color=WHITE,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(song.artist,color=MUTED,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick={favorite(song)}){Icon(if(song.id.toString() in favs)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=PURPLE)};IconButton(onClick={if(playing)p?.pause()else p?.play()}){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=PURPLE)};IconButton(onClick=next){Icon(Icons.Default.SkipNext,null,tint=PURPLE)}}}}
 }
}
@Composable private fun Disc(playing:Boolean){val spin=remember{Animatable(0f)};LaunchedEffect(playing){while(playing)spin.animateTo(spin.value+360f,tween(5000,easing=LinearEasing))}
 Box(Modifier.size(270.dp).rotate(spin.value).background(Color(0xFF292133),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(232.dp).background(Color(0xFF111018),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(190.dp).background(Color(0xFF30283D),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(126.dp).background(ACCENT,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Headphones,null,tint=Color.White,modifier=Modifier.size(58.dp))}}};Box(Modifier.size(10.dp).background(Color.Black,CircleShape))}}
