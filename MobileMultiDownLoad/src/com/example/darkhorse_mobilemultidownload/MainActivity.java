package com.example.darkhorse_mobilemultidownload;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {
	Handler handler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			tv.setText((long)pb.getProgress() * 100 /pb.getMax() + "%");
		}
	};
	
	int ThreadCount = 3;
	int finishThread = 0;
	int currentProgress;
	String pathname = "android-async-http-master.zip";
	String path  = "http://10.100.36.71:8080/AndroidServer/"+pathname;
	ProgressBar pb;
	TextView tv;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		pb = (ProgressBar) findViewById(R.id.pb);
		tv = (TextView) findViewById(R.id.tv);
	}
	public void click(View v){
		Thread t = new Thread(){
			@Override
			public void run() {
				//发送get请求，请求地址资源(获取地址长度)
				try {
					URL url = new URL(path);
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setConnectTimeout(5000);
					conn.setReadTimeout(5000);
					
					if(conn.getResponseCode() == 200){
						//拿到所请求资源文件长度
						int length = conn.getContentLength();
						//设置进度条的最大长度
						pb.setMax(length);
						File file  = new File(Environment.getExternalStorageDirectory(),pathname);
						//生成临时文件(此方法替代FileOutputStream)
						RandomAccessFile raf = new RandomAccessFile(file, "rwd");
						//设置临时文件大小
						raf.setLength(length);
						raf.close();
						//计算出每个线程应该下载多少字节
						int size = length / ThreadCount;
						
						for (int i = 0; i < ThreadCount; i++) {
							//计算线程下载的开始位置和结束位置
							int startIndex = i * size;
							int endIndex = (i + 1) * size -1 ;
							//当线程位最后一个线程时，结束位置发生改变，写死
							if(i == ThreadCount -1){
								endIndex = length -1;
							}
							new DownLoadThread(startIndex, endIndex, i).start();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		t.start();
	}
	class DownLoadThread extends Thread{
		int startIndex ;
		int endIndex ;
		int threadId;
		public DownLoadThread(int startIndex, int endIndex, int threadId) {
			super();
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.threadId = threadId;
		}
		@Override
		public void run() {
			//发送get请求，请求资源地址
			try {
				File progressFile = new File(Environment.getExternalStorageDirectory(),"线程"+threadId+".txt");
				//判断是否存在临时进度文件
				if(progressFile.exists()){
					FileInputStream fis = new FileInputStream(progressFile);
					BufferedReader br = new BufferedReader(new InputStreamReader(fis));
					int lastProgress = Integer.parseInt(br.readLine());
					startIndex += currentProgress;
					
					//把上次下载的进度显示至进度条
					currentProgress += lastProgress;
					pb.setProgress(currentProgress);
					//发送消息给主线程，让主线程刷新ui
					handler.sendEmptyMessage(1);
					fis.close();
					br.close();
				}
				System.out.println("线程:" + threadId +"的下载区间为:" + startIndex + "------" + endIndex);
				URL url = new URL(path);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setConnectTimeout(5000);
				conn.setReadTimeout(5000);
				//设置本次Http请求的数据区间
				conn.setRequestProperty("Range", "bytes="+startIndex+"-"+endIndex);
				
				//请求部分数据响应码为206
				if(conn.getResponseCode() == 206){
					//此时流里只有1/3源文件数据
					InputStream is = conn.getInputStream();
					byte[] b = new byte[1024];
					int len = 0 ;
					int total = 0;
					//拿到临时文件输出流
					File file  = new File(Environment.getExternalStorageDirectory(),pathname);
					RandomAccessFile raf = new RandomAccessFile(file, "rwd");
					//把文件写入的位置移动至startIndex
					raf.seek(startIndex);
					while((len = is.read(b)) != -1){
						//每次读取的数据同步写入临时文件
						raf.write(b,0,len);
						total += len;
						System.out.println("线程："+ threadId + "下载了" + total + "字节");
						
						//每次读取的数据，将本次读取的数据显示至进度条
						currentProgress += len;
						pb.setProgress(currentProgress);
						
						//发送消息给主线程，让主线程刷新ui
						handler.sendEmptyMessage(1);
						//创建一个专门用来记录下载进度的临时文件
						RandomAccessFile progressRaf = new RandomAccessFile(progressFile, "rwd");
						//每次读取流里的数据之后，同步把当前线程下载的总进度写入进度文件中
						progressRaf.write((total + "").getBytes());
						progressRaf.close();
					}
					System.out.println("线程：" + threadId +"现在已完成－－－－");
					raf.close();
					
					//线程全部执行完毕时，开始删除临时文件
					finishThread++;
					synchronized (path) {
						if(finishThread == ThreadCount ){
							for (int i = 0; i < ThreadCount; i++) {
								File killFile = new File(Environment.getExternalStorageDirectory(),"线程" + i + ".txt");
								killFile.delete();
							}
							finishThread = 0;
						}
					}
					
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
