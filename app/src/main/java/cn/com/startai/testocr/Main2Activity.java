package cn.com.startai.testocr;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.ImageUtils;
import com.blankj.utilcode.util.ResourceUtils;
import com.blankj.utilcode.util.ThreadUtils;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.devio.takephoto.app.TakePhoto;
import org.devio.takephoto.app.TakePhotoImpl;
import org.devio.takephoto.model.CropOptions;
import org.devio.takephoto.model.InvokeParam;
import org.devio.takephoto.model.TContextWrap;
import org.devio.takephoto.model.TResult;
import org.devio.takephoto.permission.InvokeListener;
import org.devio.takephoto.permission.PermissionManager;
import org.devio.takephoto.permission.TakePhotoInvocationHandler;
import org.xutils.common.Callback;
import org.xutils.common.task.AbsTask;
import org.xutils.common.util.FileUtil;
import org.xutils.http.RequestParams;
import org.xutils.x;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.com.startai.common.CommonSDKInterface;
import cn.com.startai.common.utils.TAndL;
import cn.com.startai.common.utils.permission.CPermissionHelper;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

public class Main2Activity extends AppCompatActivity implements TakePhoto.TakeResultListener, InvokeListener {

    //训练数据路径，必须包含tesseract文件夹
    static final String TESSBASE_PATH = "/storage/emulated/0/Download/tesseract/";
    //识别语言英文
    static final String DEFAULT_LANGUAGE = "eng";
    //识别语言简体中文
    static final String CHINESE_LANGUAGE = "chi_sim";

    static final String TESS_FORLDER = "tessdata";

    static final String EXTNAME = "traineddata";

    static final String CN_FILE = TESSBASE_PATH + TESS_FORLDER + "/" + CHINESE_LANGUAGE + "." + EXTNAME;
    static final String EN_FILE = TESSBASE_PATH + TESS_FORLDER + "/" + DEFAULT_LANGUAGE + "." + EXTNAME;

    @BindView(R.id.bt1)
    Button bt1;
    @BindView(R.id.bt2)
    Button bt2;
    @BindView(R.id.toggleButton)
    ToggleButton toggleButton;
    private AlertDialog.Builder alertDialog;
    private TakePhoto takePhoto;
    private InvokeParam invokeParam;
    private Uri imageUri;
    private String TAG = "Main2Activity";
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        ButterKnife.bind(this);

        CommonSDKInterface.getInstance().init(getApplication());

        alertDialog = new AlertDialog.Builder(this);
        progressDialog = new ProgressDialog(this, ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage("正在加载资源文件");
        progressDialog.show();

        initHeadCacheFile();

        x.Ext.init(getApplication());

        initOcrData();

    }

    private void initOcrData() {
        if (FileUtils.isFileExists(CN_FILE) && FileUtils.isFileExists(EN_FILE)) {
            TAndL.TL( "资源文件加载完成");
            dismissProgressDialog();
        } else {

            CPermissionHelper.requestStorage(new CPermissionHelper .OnPermissionGrantedListener() {
                @Override
                public void onPermissionGranted() {
                    ThreadUtils.executeByIo(new ThreadUtils.Task<Object>() {
                        @Nullable
                        @Override
                        public Object doInBackground() throws Throwable {

                            boolean copyResult = ResourceUtils.copyFileFromAssets("ocr", TESSBASE_PATH + TESS_FORLDER);
                            TAndL.L("copyResult = " + copyResult);
                            return null;
                        }

                        @Override
                        public void onSuccess(@Nullable Object result) {
                            dismissProgressDialog();
                            TAndL.TL(  "资源文件加载完成");
                        }

                        @Override
                        public void onCancel() {
                            dismissProgressDialog();
                            FileUtils.delete(CN_FILE);
                            FileUtils.delete(EN_FILE);
                        }

                        @Override
                        public void onFail(Throwable t) {
                            dismissProgressDialog();
                            FileUtils.delete(CN_FILE);
                            FileUtils.delete(EN_FILE);
                        }
                    });
                }
            }, new CPermissionHelper.OnPermissionDeniedListener() {
                @Override
                public void onPermissionDenied() {
                    TAndL.TL( "请授权");
                }
            });


        }


    }


    /*
     * 获取参数的json对象
     */
    public static JSONObject getParam(int type, String dataValue) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("dataType", type);
            obj.put("dataValue", dataValue);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }


    private void orcLocal(final String imgFile) {


        ThreadUtils.executeByIo(new ThreadUtils.Task<String>() {
            @Nullable
            @Override
            public String doInBackground() throws Throwable {

                //获取缓存的bitmap
                final TessBaseAPI baseApi = new TessBaseAPI();
                //初始化OCR的训练数据路径与语言
                baseApi.init(TESSBASE_PATH, CHINESE_LANGUAGE);
                //设置识别模式
                baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                //设置要识别的图片

                Bitmap bmp = ImageUtils.getBitmap(imgFile);

//                bmp = ImageUtils.toGray(bmp);//灰度化

//                bmp = zeroAndOne(bmp); //二值化

                baseApi.setImage(bmp);

                String utf8Text = baseApi.getUTF8Text();
                baseApi.clear();
                baseApi.end();

                Log.i(TAG, "result = " + utf8Text);
                return utf8Text;
            }

            @Override
            public void onSuccess(@Nullable String result) {
                dismissProgressDialog();
                alertText("result", result);
            }

            @Override
            public void onCancel() {
                dismissProgressDialog();
                alertText("result", "onCancel");
            }

            @Override
            public void onFail(Throwable t) {
                dismissProgressDialog();
                alertText("result", "onFail" + t.getMessage());
            }
        });


    }

    public void ocrCloud(final String imgFile) {


        ThreadUtils.executeByIo(new ThreadUtils.Task<String>() {
            @Nullable
            @Override
            public String doInBackground() throws Throwable {

                String host = "https://ocrhcp.market.alicloudapi.com";
                String path = "/api/predict/ocr_train_ticket";
                String appcode = "b2c42e9ff89c4627860a170b51f2eb01";
                Boolean is_old_format = false;//如果文档的输入中含有inputs字段，设置为True， 否则设置为False

                String config_str = "";

                Map<String, String> headers = new HashMap<String, String>();
                //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
                headers.put("Authorization", "APPCODE " + appcode);

                // 对图像进行base64编码
                String imgBase64 = "";
                try {
                    File file = new File(imgFile);
                    byte[] content = new byte[(int) file.length()];
                    FileInputStream finputstream = new FileInputStream(file);
                    finputstream.read(content);
                    finputstream.close();
                    imgBase64 = new String(encodeBase64(content));
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
                // 拼装请求body的json字符串
                JSONObject requestObj = new JSONObject();
                try {
                    if (is_old_format) {
                        JSONObject obj = new JSONObject();
                        obj.put("image", getParam(50, imgBase64));
                        if (config_str.length() > 0) {
                            obj.put("configure", getParam(50, config_str));
                        }
                        JSONArray inputArray = new JSONArray();
                        inputArray.add(obj);
                        requestObj.put("inputs", inputArray);
                    } else {
                        requestObj.put("image", imgBase64);
                        if (config_str.length() > 0) {
                            requestObj.put("configure", config_str);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                String bodys = requestObj.toString();

                try {

                    RequestParams param = new RequestParams(host + path);
                    param.setHeader("Authorization", "APPCODE " + appcode);
                    param.setBodyContent(bodys);
                    String result = x.http().postSync(param, String.class);

                    Log.i(TAG, "result = " + result);
                    return result;
                } catch (Exception e) {
                    e.printStackTrace();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }

                return null;
            }

            @Override
            public void onSuccess(@Nullable String result) {
                dismissProgressDialog();
                if (result != null) {
                    alertText("result", result);
                }
            }

            @Override
            public void onCancel() {
                dismissProgressDialog();
                TAndL.TL(  "onCancel");
            }

            @Override
            public void onFail(Throwable t) {
                dismissProgressDialog();
                TAndL.TL(  "onFail");
            }
        });


    }


    private void initHeadCacheFile() {
        //拍照存储路径
        File file = new File(Environment.getExternalStorageDirectory(), "/TestOCR/" + System.currentTimeMillis() + ".jpg");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        imageUri = Uri.fromFile(file);
    }

    /**
     * 裁剪参数
     *
     * @return
     */
    private CropOptions getCropOptions() {

        int height = 488;
        int width = 488;
        boolean withWonCrop = true;

        CropOptions.Builder builder = new CropOptions.Builder();

        builder.setOutputX(width).setOutputY(height);
        builder.setWithOwnCrop(withWonCrop);
        return builder.create();
    }

    /**
     * 获取TakePhoto实例
     *
     * @return
     */
    public TakePhoto getTakePhoto() {
        if (takePhoto == null) {
            takePhoto = (TakePhoto) TakePhotoInvocationHandler.of(this).bind(new TakePhotoImpl(this, this));
        }
        return takePhoto;

    }

    @Override
    public PermissionManager.TPermissionType invoke(InvokeParam invokeParam) {
        PermissionManager.TPermissionType type = PermissionManager.checkPermission(TContextWrap.of(this), invokeParam.getMethod());
        if (PermissionManager.TPermissionType.WAIT.equals(type)) {
            this.invokeParam = invokeParam;
        }
        return type;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //以下代码为处理Android6.0、7.0动态权限所需
        PermissionManager.TPermissionType type = PermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionManager.handlePermissionsResult(this, type, invokeParam, this);
    }

    /**
     * 选择完相册图片返回
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        getTakePhoto().onActivityResult(requestCode, resultCode, data);
    }

    @OnClick({R.id.bt1, R.id.bt2})
    public void onViewClicked(View view) {


        switch (view.getId()) {
            case R.id.bt1:

                //选择相册
                getTakePhoto().onPickFromGalleryWithCrop(imageUri, getCropOptions());

                break;
            case R.id.bt2:
                //相机
                getTakePhoto().onPickFromCaptureWithCrop(imageUri, getCropOptions());


                break;
        }
    }

    private void alertText(final String title, final String message) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alertDialog.setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("确定", null)
                        .show();
            }
        });
    }

    @Override
    public void takeSuccess(TResult result) {
        String originalPath = result.getImage().getOriginalPath();
        TAndL.TL( "takeSuccess " + originalPath);

        alertProgress("正在识别。。。");

        if (toggleButton.isChecked()) {
            ocrCloud(originalPath);
        } else {
            orcLocal(originalPath);
        }

    }

    @Override
    public void takeFail(TResult result, String msg) {
        TAndL.TL( "takeFail ");
    }

    @Override
    public void takeCancel() {
        TAndL.TL(  "takeCancel ");
    }


    void dismissProgressDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();

                }
            }
        });
    }

    void alertProgress(final String text) {
        dismissProgressDialog();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressDialog.setMessage(text);
                progressDialog.show();
            }
        });
    }

    // 二值化处理
    private Bitmap zeroAndOne(Bitmap bm) {
        int width = bm.getWidth();//原图像宽度
        int height = bm.getHeight();//原图像高度
        int color;//用来存储某个像素点的颜色值
        int r, g, b, a;//红，绿，蓝，透明度
        //创建空白图像，宽度等于原图宽度，高度等于原图高度，用ARGB_8888渲染，这个不用了解，这样写就行了
        Bitmap bmp = Bitmap.createBitmap(width, height
                , Bitmap.Config.ARGB_8888);

        int[] oldPx = new int[width * height];//用来存储原图每个像素点的颜色信息
        int[] newPx = new int[width * height];//用来处理处理之后的每个像素点的颜色信息
        /**
         * 第一个参数oldPix[]:用来接收（存储）bm这个图像中像素点颜色信息的数组
         * 第二个参数offset:oldPix[]数组中第一个接收颜色信息的下标值
         * 第三个参数width:在行之间跳过像素的条目数，必须大于等于图像每行的像素数
         * 第四个参数x:从图像bm中读取的第一个像素的横坐标
         * 第五个参数y:从图像bm中读取的第一个像素的纵坐标
         * 第六个参数width:每行需要读取的像素个数
         * 第七个参数height:需要读取的行总数
         */
        bm.getPixels(oldPx, 0, width, 0, 0, width, height);//获取原图中的像素信息

        for (int i = 0; i < width * height; i++) {//循环处理图像中每个像素点的颜色值
            color = oldPx[i];//取得某个点的像素值
            r = Color.red(color);//取得此像素点的r(红色)分量
            g = Color.green(color);//取得此像素点的g(绿色)分量
            b = Color.blue(color);//取得此像素点的b(蓝色分量)
            a = Color.alpha(color);//取得此像素点的a通道值

            //此公式将r,g,b运算获得灰度值，经验公式不需要理解
            int gray = (int) ((float) r * 0.3 + (float) g * 0.59 + (float) b * 0.11);
            //下面前两个if用来做溢出处理，防止灰度公式得到到灰度超出范围（0-255）
            if (gray > 255) {
                gray = 255;
            }

            if (gray < 0) {
                gray = 0;
            }

            if (gray != 0) {//如果某像素的灰度值不是0(黑色)就将其置为255（白色）
                gray = 255;
            }

            newPx[i] = Color.argb(a, gray, gray, gray);//将处理后的透明度（没变），r,g,b分量重新合成颜色值并将其存储在数组中
        }
        /**
         * 第一个参数newPix[]:需要赋给新图像的颜色数组//The colors to write the bitmap
         * 第二个参数offset:newPix[]数组中第一个需要设置给图像颜色的下标值//The index of the first color to read from pixels[]
         * 第三个参数width:在行之间跳过像素的条目数//The number of colors in pixels[] to skip between rows.
         * Normally this value will be the same as the width of the bitmap,but it can be larger(or negative).
         * 第四个参数x:从图像bm中读取的第一个像素的横坐标//The x coordinate of the first pixels to write to in the bitmap.
         * 第五个参数y:从图像bm中读取的第一个像素的纵坐标//The y coordinate of the first pixels to write to in the bitmap.
         * 第六个参数width:每行需要读取的像素个数The number of colors to copy from pixels[] per row.
         * 第七个参数height:需要读取的行总数//The number of rows to write to the bitmap.
         */
        bmp.setPixels(newPx, 0, width, 0, 0, width, height);//将处理后的像素信息赋给新图
        return bmp;//返回处理后的图像
    }
}
