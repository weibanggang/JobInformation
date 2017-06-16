package jobinf;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jayway.jsonpath.JsonPath;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;


/**
 * Created by ishare on 17-6-14.
 */
public class GetJobInf {

    static List<String> joburls;
    static List<Job> jobList=new ArrayList<>();

    public static void main(String[] args) throws IOException, WriteException, BiffException, InterruptedException {

        String gj="应届毕业生";
        String city="全国";
        String kd="Python";

        JobCondition.setGj(gj);
        JobCondition.setCity(city);
        JobCondition.setKd(kd);
        JobCondition.setFilename(kd+"_"+gj+"_"+city+".xls");  //文件名
        GetJobInf getJobInf=new GetJobInf();

         joburls=getJobInf.getJobUrls(JobCondition.gj,JobCondition.city,JobCondition.kd);


        long start=System.currentTimeMillis();

        List<GetThread> threads=new ArrayList<>();
        CountDownLatch latch=new CountDownLatch(joburls.size());

        for (int i=0;i<joburls.size();i++){
            GetThread getThread=new GetThread(joburls.get(i),latch);
            getThread.start();
            threads.add(getThread);
        }

        latch.await();//等待线程结束

        for (GetThread getThread:threads){
            jobList.add(getThread.job);
        }

        /*for (String u:joburls){
            jobList.add(getJobInf.getJob(u));
        }*/

        long end=System.currentTimeMillis();
        System.out.println(end-start);
        getJobInf.initExcel();
        getJobInf.insertExcel(jobList);


    }


    void initExcel() throws IOException, WriteException {
        WritableWorkbook writeBook = Workbook.createWorkbook(new File(JobCondition.filename));

        // 2、新建工作表(sheet)对象，并声明其属于第几页
        WritableSheet firstSheet = writeBook.createSheet("job", 1);// 第一个参数为工作簿的名称，第二个参数为页数

        //Label label1 = new Label(0, 0, "职位名称");//从0开始的，第一个参数指定单元格的列数、第二个参数指定单元格的行数，第三个指定写的字符串内容

        firstSheet.addCell(new Label(0,0,"职位名称"));
        firstSheet.addCell(new Label(1,0,"工资"));
        firstSheet.addCell(new Label(2,0,"职位描述"));
        firstSheet.addCell(new Label(3,0,"公司名称"));
        firstSheet.addCell(new Label(4,0,"公司主页"));
        firstSheet.addCell(new Label(5,0,"详情网页"));

        writeBook.write();

        writeBook.close();
    }
    void insertExcel(List<Job> jobList) throws IOException, BiffException, WriteException {
       int row=1;
        Workbook wb = Workbook.getWorkbook(new File(JobCondition.filename));
        WritableWorkbook book = Workbook.createWorkbook(new File(JobCondition.filename), wb);

        WritableSheet sheet=book.getSheet(0);

        for (int i=0;i<jobList.size();i++){
            sheet.addCell(new Label(0,row,jobList.get(i).getJobname()));
            sheet.addCell(new Label(1,row,jobList.get(i).getSalary()));
            sheet.addCell(new Label(2,row,jobList.get(i).getJobdesc()));
            sheet.addCell(new Label(3,row,jobList.get(i).getCompany()));
            sheet.addCell(new Label(4,row,jobList.get(i).getCompanysite()));
            sheet.addCell(new Label(5,row,jobList.get(i).getJobdsite()));
            row++;
        }

        book.write();
        book.close();
    }

    public List<String> getJobUrls(String gj,String city,String kd){

        //String gj="应届毕业生";  //限制条件
        //String city="广州";//城市
        //String kd="Java";//编程语言
        int pn=1;//开始页数
        String pre_url="https://www.lagou.com/jobs/";
        String end_url=".html";
        String url;

        if (gj.equals("")){

            url="https://www.lagou.com/jobs/positionAjax.json?px=default&city="+city+"&needAddtionalResult=false&first=false&pn="+pn+"&kd="+kd;

        }else {

            url="https://www.lagou.com/jobs/positionAjax.json?gj="+gj+"&px=default&city="+city+"&needAddtionalResult=false&first=false&pn="+pn+"&kd="+kd;

        }


        String rs=getJson(url);


        //System.out.println(rs);
        int total= JsonPath.read(rs,"$.content.positionResult.totalCount");//获取总数
        int pagesize=total/15;
        if (pagesize>=30){
            pagesize=30;
        }

        System.out.println(total);
        //System.out.println(rs);

        List<Integer> posid=JsonPath.read(rs,"$.content.positionResult.result[*].positionId");//获取网页id

        for (int j=1;j<=pagesize;j++){  //获取所有的网页id
            pn++;
            String rs2=getJson(url);
            List<Integer> posid2=JsonPath.read(rs2,"$.content.positionResult.result[*].positionId");
            posid.addAll(posid2); //添加解析的id到第一个list
        }

        List<String> joburls=new ArrayList<>();

        //生成网页
        for (int id:posid){
            String url3=pre_url+id+end_url;
            joburls.add(url3);
        }


        return joburls;

    }


    public Job getJob(String url){  //获取工作信息
        Job job=new Job();
        Document document= null;
        document = Jsoup.parse(getJson(url));

        job.setJobname(document.select(".name").text());
        job.setSalary(document.select(".salary").text());
        String joball=HtmlTool.tag(document.select(".job_bt").select("div").html());//清除html标签
        job.setJobdesc(joball);//职位描述包含要求
        job.setCompany(document.select(".b2").attr("alt"));
        Elements elements=document.select(".c_feature");
        //System.out.println(document.select(".name").text());
        job.setCompanysite(elements.select("a").attr("href")); //获取公司主页
        job.setJobdsite(url);
        return job;
    }

   /* private String getDesc(String desc){
        System.out.println(desc);
        return desc.substring(desc.indexOf("岗位职责")+5,desc.indexOf("岗位要求"));
    }

    private String getRequest(String re){
        return re.substring(re.indexOf("任职要求")+5);
    }*/

    public static String getJson(String url){
        String rs="";
        BufferedReader in = null;
        try {
            URL url1=new URL(url);
            URLConnection connection=url1.openConnection();
            connection.setRequestProperty("Host","www.lagou.com");
            connection.setRequestProperty("Connection","keep-alive");
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36");
            connection.setRequestProperty("Cookie","user_trace_token=20170613231153-cab4a0ae19044241a78f862a01f538ab; LGUID=20170613231201-a6a4d5dd-504a-11e7-9b38-5254005c3644; index_location_city=%E6%B7%B1%E5%9C%B3; JSESSIONID=ABAAABAAAHAAAFDF4305BA75AFD92CCBA526F196E918378; X_HTTP_TOKEN=22a5137c4a6f29a2ebb6032760214008; _ga=GA1.2.1967947591.1497366717; _gid=GA1.2.2036779968.1497366717; _gat=1; Hm_lvt_4233e74dff0ae5bd0a3d81c6ccf756e6=1497366721,1497409696,1497443385,1497487138; Hm_lpvt_4233e74dff0ae5bd0a3d81c6ccf756e6=1497537506; _ga=GA1.3.1967947591.1497366717; LGSID=20170615212347-dccea570-51cd-11e7-9c5e-5254005c3644; LGRID=20170615223826-4ab2e4fc-51d8-11e7-9c5e-5254005c3644");
            //connection.setReadTimeout(8000);
            in = new BufferedReader(new InputStreamReader(
                    connection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                rs += line;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return rs;
    }

}

