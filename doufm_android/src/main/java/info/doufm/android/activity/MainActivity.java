package info.doufm.android.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonArrayRequest;
import com.umeng.analytics.MobclickAgent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import cn.pedant.SweetAlert.SweetAlertDialog;
import info.doufm.android.R;
import info.doufm.android.adapter.ChannelListAdapter;
import info.doufm.android.info.MusicInfo;
import info.doufm.android.info.PlaylistInfo;
import info.doufm.android.network.RequestManager;
import info.doufm.android.playview.MySeekBar;
import info.doufm.android.playview.RotateAnimator;
import info.doufm.android.user.UserUtil;
import info.doufm.android.utils.CacheUtil;
import info.doufm.android.utils.Constants;
import info.doufm.android.utils.ShareUtil;
import info.doufm.android.utils.TimeFormat;
import libcore.io.DiskLruCache;


public class MainActivity extends ActionBarActivity implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnPreparedListener {

    private static final String TAG = "seekBar";
    private ListView mDrawerList;

    private Toolbar mToolbar;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mActionBarDrawerToggle;

    private File cacheDir;
    private DiskLruCache mDiskLruCache = null;
    private MusicInfo playMusicInfo;
    private MusicInfo nextMusicInfo;
    private MusicInfo preMusicInfo;
    private boolean hasNextCache = false;
    private DownloadMusicThread mDownThread;
    private int bufPercent = 0;
    private boolean seekNow = false;
    private TextView tvTotalTime;
    private TextView tvCurTime;
    private boolean playLoopFlag = false;

    private int colorNum;

    //菜单列表监听器
    private ListListener mListLisener;

    //播放界面相关
    private Button btnPlay;
    private Button btnNextSong;
    private Button btnPreSong;
    private Button btnPlayMode;
    private Button btnLove;
    private MySeekBar seekBar;
    private ImageView ivNeedle;
    private ImageView ivDisk;
    private RotateAnimator mDiskAnimator;
    private boolean isLoadingSuccess = false;
    private int mMusicDuration;            //音乐总时间
    private int mMusicCurrDuration;        //当前播放时间
    private Animation needleUpAnim;
    private Animation needleDownAnim;
    private Animation needleAnim;
    private Timer mTimer = new Timer();     //计时器
    private List<String> mLeftResideMenuItemTitleList = new ArrayList<String>();
    private List<PlaylistInfo> mPlaylistInfoList = new ArrayList<PlaylistInfo>();
    private int mPlayListNum = 0;
    private int mThemeNum = 0;
    private boolean isFirstLoad = true;
    private boolean needleDownFlag = false;  //是否需要play needledown的动画
    private boolean loveFlag = false;
    private Menu menu;
    private ChannelListAdapter channelListAdapter;

    //用户操作类对象
    private UserUtil mUserUtil;

    private ShareUtil mShareUtil;

    private boolean isPlay = false;
    //定义Handler对象
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //处理消息
            if (msg.what == Constants.DISMISS) {
                btnNextSong.setEnabled(true);
                btnPlay.setEnabled(true);
            }
            if (msg.what == Constants.UPDATE_TIME) {
                //更新音乐播放状态
                if (isPlay) {
                    mMusicCurrDuration = mMainMediaPlayer.getCurrentPosition();
                    tvCurTime.setText(TimeFormat.msToMinAndS(mMusicCurrDuration));
                    seekBar.setProgress(mMusicCurrDuration);
                }
            }
        }
    };
    //来电标志:只当正在播放的情况下有来电时置为true
    private boolean phoneCome = false;
    //播放器
    private MediaPlayer mMainMediaPlayer;
    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {

            if (mMainMediaPlayer == null) {
                return;
            }
            if (mMainMediaPlayer.isPlaying() && !seekNow) {
                //处理播放
                Message msg = new Message();
                msg.what = Constants.UPDATE_TIME;
                handler.sendMessage(msg);
            }
        }
    };

    //private String PLAYLIST_URL = "http://115.29.140.122:5001/api/playlist/?start=0";
    private boolean firstErrorFlag = true;
    private Response.ErrorListener errorListener = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError volleyError) {
            timerTask.cancel();
            if (firstErrorFlag) {
                firstErrorFlag = false;
                new SweetAlertDialog(MainActivity.this, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("网络连接出错啦...")
                        .setConfirmText("退出")
                        .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                            @Override
                            public void onClick(SweetAlertDialog sDialog) {
                                finish();
                                System.exit(0);
                            }
                        })
                        .show();
            }
        }
    };

    private long exitTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        tvCurTime = (TextView) findViewById(R.id.curTimeText);
        tvTotalTime = (TextView) findViewById(R.id.totalTimeText);
        ivNeedle = (ImageView) findViewById(R.id.iv_needle);
        ivDisk = (ImageView) findViewById(R.id.iv_disk);
        needleUpAnim = AnimationUtils.loadAnimation(this, R.anim.rotation_up);
        needleDownAnim = AnimationUtils.loadAnimation(this, R.anim.rotation_down);
        needleAnim = AnimationUtils.loadAnimation(this, R.anim.rotation_up_down);
        mDiskAnimator = new RotateAnimator(this, ivDisk);
        colorNum = Constants.BACKGROUND_COLORS.length;
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mToolbar = (Toolbar) findViewById(R.id.toolbar_custom);
        mToolbar.setTitle("DouFM");
        mToolbar.setTitleTextColor(Color.WHITE);
        mToolbar.setSubtitleTextColor(Color.WHITE);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mDrawerList = (ListView) findViewById(R.id.navdrawer);
        mDrawerList.setVerticalScrollBarEnabled(false);
        playMusicInfo = new MusicInfo();
        nextMusicInfo = new MusicInfo();
        mUserUtil = new UserUtil();

        mActionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, R.string.drawer_open, R.string.drawer_close) {

            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu();
            }

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu();
            }
        };
        mDrawerLayout.setDrawerListener(mActionBarDrawerToggle);
        mActionBarDrawerToggle.syncState();

        btnPlay = (Button) findViewById(R.id.btn_start_play);
        btnNextSong = (Button) findViewById(R.id.btn_play_next);
        btnPreSong = (Button) findViewById(R.id.btn_play_previous);
        btnPlayMode = (Button) findViewById(R.id.btn_play_mode);
        btnLove = (Button) findViewById(R.id.btn_love);
        seekBar = (MySeekBar) findViewById(R.id.seekbar);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBar.setProgress(progress);
                mMusicCurrDuration = progress;
                tvCurTime.setText(TimeFormat.msToMinAndS(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekNow = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mMusicDuration != 0 && mMusicCurrDuration <= (mMusicDuration * bufPercent / 100)) {
                    mMainMediaPlayer.seekTo(mMusicCurrDuration);
                } else {
                    mMusicCurrDuration = mMainMediaPlayer.getCurrentPosition();
                    tvCurTime.setText(TimeFormat.msToMinAndS(mMusicCurrDuration));
                    seekBar.setProgress(mMusicCurrDuration);
                }
                seekNow = false;
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isPlay) {
                    isPlay = false;
                    ivNeedle.startAnimation(needleUpAnim);
                    btnPlay.setBackgroundResource(R.drawable.btn_start_play);
                    mMainMediaPlayer.pause();
                    mDiskAnimator.pause();
                } else {
                    isPlay = true;
                    ivNeedle.startAnimation(needleDownAnim);
                    btnPlay.setBackgroundResource(R.drawable.btn_stop_play);
                    mMainMediaPlayer.start();
                    mDiskAnimator.play();
                }
            }
        });

        btnNextSong.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                preMusicInfo = playMusicInfo;
                btnPreSong.setClickable(true);
                if (hasNextCache) {
                    playMusicInfo = nextMusicInfo;
                    nextMusicInfo = new MusicInfo();
                    playCacheMusic();
                    hasNextCache = false;
                } else {
                    if (mDownThread != null) {
                        mDownThread.runFlag = false;
                        mDownThread = null;
                    }
                    playMusicInfo = new MusicInfo();
                    playRandomMusic(mPlaylistInfoList.get(mPlayListNum).getKey());
                }
            }
        });

        btnPreSong.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mDownThread != null) {
                    mDownThread.runFlag = false;
                    mDownThread = null;
                }
                playMusicInfo = preMusicInfo;
                btnPreSong.setClickable(false);
                String key = CacheUtil.hashKeyForDisk(playMusicInfo.getAudio());
                try {
                    if (mDiskLruCache.get(key) != null) {
                        changeMusic(true);
                        mMainMediaPlayer.setDataSource(cacheDir.toString() + "/" + key + ".0");
                        mMainMediaPlayer.prepare();
                        seekBar.setSecondaryProgress(seekBar.getMax());
                    } else {
                        changeMusic(false);
                        mMainMediaPlayer.setDataSource(playMusicInfo.getAudio());
                        mMainMediaPlayer.prepareAsync();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                getCoverImageRequest(playMusicInfo);
            }
        });

        //单曲循环/随便播放按钮点击响应
        btnPlayMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playLoopFlag) {
                    btnPlayMode.setBackgroundResource(R.drawable.bg_btn_shuffle);//随机播放
                    Toast.makeText(getApplicationContext(), "随机播放", Toast.LENGTH_SHORT).show();
                    mMainMediaPlayer.setLooping(false);
                } else {
                    btnPlayMode.setBackgroundResource(R.drawable.bg_btn_one);//单曲循环
                    Toast.makeText(getApplicationContext(), "单曲循环", Toast.LENGTH_SHORT).show();
                    mMainMediaPlayer.setLooping(true);
                }
                playLoopFlag = !playLoopFlag;
            }
        });

        btnLove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loveFlag) {
                    btnLove.setBackgroundResource(R.drawable.bg_btn_love);
                    Toast.makeText(getApplicationContext(), "您已取消收藏", Toast.LENGTH_SHORT).show();
                } else {
                    btnLove.setBackgroundResource(R.drawable.bg_btn_loved);
                    Toast.makeText(getApplicationContext(), "您已收藏本歌", Toast.LENGTH_SHORT).show();
                }
                loveFlag = !loveFlag;
            }
        });
        btnPreSong.setClickable(false); //一定要在绑定监听器之后

        try {
            cacheDir = CacheUtil.getDiskCacheDir(this, "music");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            mDiskLruCache = DiskLruCache.open(cacheDir, CacheUtil.getAppVersion(this), 1, CacheUtil.DISK_CACHE_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mShareUtil = new ShareUtil(this);
        mThemeNum = mShareUtil.getTheme();
        mToolbar.setBackgroundColor(Color.parseColor(Constants.ACTIONBAR_COLORS[mThemeNum]));
        mDrawerLayout.setBackgroundColor(Color.parseColor(Constants.BACKGROUND_COLORS[mThemeNum]));
        mPlayListNum = mShareUtil.getPlayList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        PhoneIncomingListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
        mListLisener = new ListListener();
        if (isFirstLoad) {
            getMusicList();
            isFirstLoad = false;
        }
        //UpdateMenu();
    }


    private void getMusicList() {
        RequestManager.getRequestQueue().add(
                new JsonArrayRequest(Constants.PLAYLIST_URL, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray jsonArray) {
                        JSONObject jo = new JSONObject();
                        try {
                            for (int i = 0; i < jsonArray.length(); i++) {
                                jo = jsonArray.getJSONObject(i);
                                PlaylistInfo playlistInfo = new PlaylistInfo();
                                playlistInfo.setKey(jo.getString("key"));
                                playlistInfo.setName(jo.getString("name"));
                                mLeftResideMenuItemTitleList.add(jo.getString("name"));
                                playlistInfo.setMusic_list(jo.getString("music_list"));
                                mPlaylistInfoList.add(playlistInfo);
                            }
                            channelListAdapter = new ChannelListAdapter(MainActivity.this, mLeftResideMenuItemTitleList);
                            mDrawerList.setAdapter(channelListAdapter);
                            mDrawerList.setOnItemClickListener(mListLisener);
                            isLoadingSuccess = true;
                            initPlayer();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, errorListener) {
                    /**
                     * 添加自定义HTTP Header
                     * @return
                     * @throws com.android.volley.AuthFailureError
                     */
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("User-Agent", "Android:1.0:2009chenqc@163.com");
                        return params;
                    }
                }
        );
    }

    private void initPlayer() {
        btnNextSong.setEnabled(false);
        btnPlay.setEnabled(false);
        mMainMediaPlayer = new MediaPlayer(); //创建媒体播放器
        mMainMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC); //设置媒体流类型
        mMainMediaPlayer.setOnCompletionListener(this);
        mMainMediaPlayer.setOnErrorListener(this);
        mMainMediaPlayer.setOnBufferingUpdateListener(this);
        mMainMediaPlayer.setOnPreparedListener(this);
        mTimer.schedule(timerTask, 0, 1000);
        playRandomMusic(mPlaylistInfoList.get(mPlayListNum).getKey());
        Toast.makeText(this, "已加载频道" + "『" + mPlaylistInfoList.get(mPlayListNum).getName() + "』", Toast.LENGTH_SHORT).show();
    }

    private void playRandomMusic(String playlist_key) {
        changeMusic(false);
        final String MUSIC_URL = Constants.MUSIC_IN_PLAYLIST_URL + playlist_key + "/?num=1";
        RequestManager.getRequestQueue().add(
                new JsonArrayRequest(MUSIC_URL, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray jsonArray) {
                        //请求随机播放音乐文件信息
                        try {
                            //Log.i(TAG,"before setSource:"+System.currentTimeMillis());
                            JSONObject jo = jsonArray.getJSONObject(0);
                            playMusicInfo.setTitle(jo.getString("title"));
                            playMusicInfo.setArtist(jo.getString("artist"));
                            playMusicInfo.setAudio(Constants.BASE_URL + jo.getString("audio"));
                            playMusicInfo.setCover(Constants.BASE_URL + jo.getString("cover"));
                            mMainMediaPlayer.setDataSource(playMusicInfo.getAudio()); //这种url路径
                            mMainMediaPlayer.prepareAsync(); //prepare自动播放
                            getCoverImageRequest(playMusicInfo);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, errorListener)
        );
    }

    private void getNextMusicInfo(String playlist_key) {
        final String MUSIC_URL = Constants.MUSIC_IN_PLAYLIST_URL + playlist_key + "/?num=1";
        RequestManager.getRequestQueue().add(
                new JsonArrayRequest(MUSIC_URL, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray jsonArray) {
                        try {
                            JSONObject jo = jsonArray.getJSONObject(0);
                            nextMusicInfo.setTitle(jo.getString("title"));
                            nextMusicInfo.setArtist(jo.getString("artist"));
                            nextMusicInfo.setAudio(Constants.BASE_URL + jo.getString("audio"));
                            nextMusicInfo.setCover(Constants.BASE_URL + jo.getString("cover"));
                            mDownThread = new DownloadMusicThread(nextMusicInfo.getAudio());
                            mDownThread.start();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        Toast.makeText(MainActivity.this, "网络出错啦，请检查校园网设置", Toast.LENGTH_SHORT).show();
                    }
                })
        );
    }

    private void changeMusic(boolean type) {
        //true for cache,false for random
        if (type) {
            if (isPlay) {
                //正在播放状态下切歌，播放needleAnim动画
                needleDownFlag = false;
                ivNeedle.startAnimation(needleAnim);
            } else {
                needleDownFlag = true;
            }
        } else {
            //如果不是首次播放
            if (mDiskAnimator.notFirstFlag) {
                needleDownFlag = true;
            }
            if (isPlay) {
                ivNeedle.startAnimation(needleUpAnim);
            }
        }
        isPlay = false;
        mMusicDuration = 0;
        btnNextSong.setEnabled(false);
        btnPlay.setEnabled(false);
        mDiskAnimator.pause();
        seekBar.setProgress(0);
        seekBar.setSecondaryProgress(0);
        tvCurTime.setText("00:00");
        tvTotalTime.setText("00:00");
        mMainMediaPlayer.reset();
    }

    private void playCacheMusic() {
        changeMusic(true);
        String key = CacheUtil.hashKeyForDisk(playMusicInfo.getAudio());
        try {
            mMainMediaPlayer.setDataSource(cacheDir.toString() + "/" + key + ".0");
            mMainMediaPlayer.prepare();
            seekBar.setSecondaryProgress(seekBar.getMax());
        } catch (IOException e) {
            e.printStackTrace();
        }
        getCoverImageRequest(playMusicInfo);
    }

    private void getCoverImageRequest(final MusicInfo musicInfo) {
        RequestManager.getRequestQueue().add(new ImageRequest(musicInfo.getCover(), new Response.Listener<Bitmap>() {
                    @Override
                    public void onResponse(Bitmap bitmap) {
                        //对齐新歌曲信息显示时间
                        ivDisk.setImageBitmap(mDiskAnimator.getCroppedBitmap(bitmap));
                        mToolbar.setTitle(musicInfo.getTitle());
                        mToolbar.setSubtitle(musicInfo.getArtist());

                    }
                }, 0, 0, null, errorListener)
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (mDrawerLayout.isDrawerOpen(mDrawerList)) {
                mDrawerLayout.closeDrawer(mDrawerList);
                mDrawerLayout.setFocusableInTouchMode(true);
            } else {
                mDrawerLayout.openDrawer(mDrawerList);
                mDrawerLayout.setFocusableInTouchMode(false);
            }
        } else if (item.getItemId() == R.id.app_about_team) {
            new SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                    .setTitleText("DouFM - Android客户端")
                    .setContentText(getResources().getString(R.string.title_activity_about))
                    .show();
        } else if (item.getItemId() == R.id.user) {    //应将弹出登录界面的控件改为自定义控件而非menu
            Intent intent = new Intent();
            intent.putExtra(Constants.EXTRA_THEME, mThemeNum);
            if (item.getTitle().equals("用户登录")) {
                intent.setClass(MainActivity.this, LoginActivity.class);
            } else if (item.getTitle().equals("个人中心")) {
                intent.setClass(MainActivity.this, UserActivity.class);
            }
            startActivity(intent);

        } else if (item.getItemId() == R.id.switch_theme) {
            int colorIndex = (int) (Math.random() * colorNum);
            if (colorIndex == colorNum) {
                colorIndex--;
            }
            if (colorIndex < 0) {
                colorIndex = 0;
            }
            mToolbar.setBackgroundColor(Color.parseColor(Constants.ACTIONBAR_COLORS[colorIndex]));
            mDrawerLayout.setBackgroundColor(Color.parseColor(Constants.BACKGROUND_COLORS[colorIndex]));
            mThemeNum = colorIndex;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mActionBarDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mActionBarDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        preMusicInfo = playMusicInfo;
        btnPreSong.setClickable(true);
        if (hasNextCache) {
            playMusicInfo = nextMusicInfo;
            nextMusicInfo = new MusicInfo();
            playCacheMusic();
            hasNextCache = false;
        } else {
            if (mDownThread != null) {
                mDownThread.runFlag = false;
                mDownThread = null;
            }
            playMusicInfo = new MusicInfo();
            playRandomMusic(mPlaylistInfoList.get(mPlayListNum).getKey());
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (mMainMediaPlayer != null) {
            mMainMediaPlayer.stop();
            mMainMediaPlayer.release();
            mMainMediaPlayer = null;
        }
        new SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                .setTitleText("网络连接出错啦...")
                .setCancelText("等待")
                .setConfirmText("退出")
                .showCancelButton(true)
                .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        return;
                    }
                })
                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        finish();
                        System.exit(0);
                    }
                })
                .show();
        return true;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //保存当前下载的进度
        if (percent != bufPercent) {
            bufPercent = percent;
            if (mMusicDuration != 0) {
                seekBar.setSecondaryProgress(mMusicDuration * bufPercent / 100);
            }
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (needleDownFlag) {
            ivNeedle.startAnimation(needleDownAnim);
        }
        mDiskAnimator.play();
        mMusicDuration = mMainMediaPlayer.getDuration();
        tvTotalTime.setText(TimeFormat.msToMinAndS(mMusicDuration));
        seekBar.setMax(mMusicDuration);
        isPlay = true;
        mMainMediaPlayer.start();
        btnPlay.setBackgroundResource(R.drawable.btn_stop_play);
        btnNextSong.setEnabled(true);
        btnPlay.setEnabled(true);
        getNextMusicInfo(mPlaylistInfoList.get(mPlayListNum).getKey());
    }

    @Override
    protected void onPause() {
        super.onPause();
        RequestManager.getRequestQueue().cancelAll(this);
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        RequestManager.getRequestQueue().cancelAll(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void PhoneIncomingListener() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(new MyPhoneListener(), PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void finish() {
        super.finish();
        mShareUtil.setTheme(mThemeNum);
        mShareUtil.setPlayList(mPlayListNum);
        mShareUtil.commit();
    }

    @Override
    public void onBackPressed() {
        //如果左边栏打开时，返回键关闭左边栏
        if (mDrawerLayout.isDrawerOpen(mDrawerList)) {
            mDrawerLayout.closeDrawer(mDrawerList);
        } else {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Toast.makeText(getApplicationContext(), "再按一次退出程序", Toast.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                System.exit(0);
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenu();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu;
        getMenuInflater().inflate(R.menu.menu_sample, menu);
        return true;
    }

    private void updateMenu() {
        MenuItem menuItem = menu.findItem(R.id.user);
        if (isLogin()) {
            if (menuItem.getTitle().equals("用户登录")) {
                menuItem.setTitle("个人中心");
            }
        } else {
            if (menuItem.getTitle().equals("个人中心")) {
                menuItem.setTitle("用户登录");
            }
        }
    }

    private boolean isLogin() {
        //判断用户是否登录
        return false;
    }

    private class ListListener implements AdapterView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (isLoadingSuccess) {
                mDrawerLayout.closeDrawers();
                mPlayListNum = position;
                mDiskAnimator.pause();
                preMusicInfo = playMusicInfo;
                btnPreSong.setClickable(true);
                if (mDownThread != null) {
                    mDownThread.runFlag = false;
                    mDownThread = null;
                }
                playMusicInfo = new MusicInfo();
                playRandomMusic(mPlaylistInfoList.get(position).getKey());
            }
        }
    }

    private class DownloadMusicThread extends Thread {

        public boolean runFlag;
        private String url;

        public DownloadMusicThread(String url) {
            super();
            this.url = url;
            runFlag = true;
        }

        @Override
        public void run() {
            super.run();
            try {
                String key = CacheUtil.hashKeyForDisk(url);
                DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                if (editor != null) {
                    OutputStream outputStream = editor.newOutputStream(0);
                    if (downloadUrlToStream(url, outputStream)) {
                        editor.commit();
                        hasNextCache = true;
                    } else {
                        editor.abort();
                    }
                }
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
            HttpURLConnection urlConnection = null;
            BufferedOutputStream out = null;
            BufferedInputStream in = null;
            try {
                final URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
                out = new BufferedOutputStream(outputStream, 8 * 1024);
                int b;
                while ((b = in.read()) != -1) {
                    if (runFlag) {
                        out.write(b);
                    } else {
                        return false;
                    }
                }
                return true;
            } catch (final IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }
    }

    private class MyPhoneListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    //来电
                    if (isPlay) {
                        mMainMediaPlayer.pause();
                        isPlay = false;
                        phoneCome = true;
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    //通话结束
                    if (phoneCome && mMainMediaPlayer != null) {
                        mMainMediaPlayer.start();
                        isPlay = true;
                        phoneCome = false;
                    }
                    break;
            }
        }
    }

}
