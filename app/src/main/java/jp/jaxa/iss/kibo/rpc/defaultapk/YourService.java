package jp.jaxa.iss.kibo.rpc.defaultapk;

import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.res.AssetManager;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

import org.opencv.aruco.Aruco;
import org.opencv.core.CvType;
import org.opencv.core.Size;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.android.Utils;
import org.opencv.imgproc.Imgproc;

/**
 * Class meant to handle commands from the Ground Data System and execute them in Astrobee
 */

public class YourService extends KiboRpcService {
    // Path Coordinates & Orientations
    private final int NUM_OF_AREA = 4;
    private final Point[] PATH_CORDS = {
            new Point (10.95d, -9.88d, 5.195d),
            new Point (10.925d, -8.875d, 3.06d),
            new Point (10.925d, -7.925d, 3.06d),
            new Point (9.167d, 6.85d, 4.945d)
    };
    private final Quaternion[] PATH_ORIENT = {
            new Quaternion (0f, 0f, -0.707f, 0.707f),
            new Quaternion (0.707f, 0f, 0f, 0.707f),
            new Quaternion (0.707f, 0f, 0f, 0.707f),
            new Quaternion (-0.707f, 0f, 0f, 0.707f)
    };

    // Image Assets File Information
    private AssetManager assetManager;

    @Override
    protected void runPlan1(){
        // The mission starts.
        api.startMission();

        // set up variables for movements
        Point point;
        Quaternion quaternion;

        // set up template image files
        String[] imageFileNames = new String[10];
        try
        {
            imageFileNames = assetManager.list("C:\\Users\\jason\\KIBO_RPC\\templateApk\\app\\src\\main\\assets");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        // set up camera
        Mat cameraMatrix;
        Mat cameraCoefficients;
        Mat undistorted = new Mat();
        Mat[] templates;
        int[] templateMatchCnt;
        int mostMatchTemplateNum;

        //
        for(int count = 0; count < NUM_OF_AREA; count++)
        {
            // moving to coordinates
            point = PATH_CORDS[count];
            quaternion = PATH_ORIENT[count];
            api.moveTo(point, quaternion, false);

            // Get a camera image.
            Mat image = api.getMatNavCam();

            /* *********************************************** */
            /* Recognize Type and Number of Items in Each Area */
            /* *********************************************** */

            detectAR(image);

            // Get Camera matrix
            cameraMatrix = new Mat(3, 3, CvType.CV_64F);
            cameraMatrix.put(0, 0, api.getNavCamIntrinsics()[0]);

            // Get Lens distortion parameters
            cameraCoefficients = new Mat(1, 5, CvType.CV_64F);
            cameraCoefficients.put(0, 0, api.getNavCamIntrinsics()[1]);
            cameraCoefficients.convertTo(cameraCoefficients, CvType.CV_64F);

            // Undistorted image
            Calib3d.undistort(image, undistorted, cameraMatrix, cameraCoefficients);

            templates = patternMatching(imageFileNames);

            templateMatchCnt = removeDuplicateItems(templates, undistorted);

            // When you recognize items, let’s set the type and number.
            mostMatchTemplateNum = getMaxIndex(templateMatchCnt);
            String itemName = imageFileNames[mostMatchTemplateNum];
            api.setAreaInfo(count, itemName.substring(0, itemName.lastIndexOf('.')), templateMatchCnt[mostMatchTemplateNum]);
        }


        // When you move to the front of the astronaut, report the rounding completion.
        api.reportRoundingCompletion();
        /* ********************************************************** */
        /* Write your code to recognize which item the astronaut has. */
        /* ********************************************************** */
        // Let's notify the astronaut when you recognize it.
        api.notifyRecognitionItem();
        /*
         ******************************************************************************************************* */
         /* Write your code to move Astrobee to the location of the target item (what the astronaut
            is looking for) */
        /*
         ******************************************************************************************************* */
        // Take a snapshot of the target item.
        api.takeTargetItemSnapshot();
    }

    @Override
    protected void runPlan2(){
        // write your plan 2 here
    }

    @Override
    protected void runPlan3(){
        // write your plan 3 here
    }

    /* Function Definitions Here */

    // Move Around KOZ
    private static void aroundKOZ (Point current, int toZone)
    {

    }

    // Detect AR
    private static void detectAR(Mat image)
    {
        Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
        List<Mat> corners = new ArrayList<>();
        Mat markerIds = new Mat();
        Aruco.detectMarkers(image, dictionary, corners, markerIds);
    }

    // Pattern Matching
    private Mat[] patternMatching(String[] images)
    {
        Mat[] temp = new Mat[10];
        for(int i = 0; i < 10; i++)
        {
            try
            {
                // open the template image file in Bitmap from the file name and convert to Mat
                InputStream inputStream = getAssets().open(images[i]);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                Mat mat = new Mat();
                Utils.bitmapToMat(bitmap, mat);

                // convert to grayscale
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);

                // assign to an array of templates
                temp[i] = mat;

                inputStream.close();
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
        }

        return temp;
    }

    private static int[] removeDuplicateItems(Mat[] templates, Mat undistortedImg)
    {
        // Number of matches for each template
        int temp[] = new int[10];

        // Get the number of template matches
        for (int i = 0; i < templates.length; i++)
        {
            // Number of matches
            int matchCnt = 0;

            // Coordinates of the matched Location
            List<org.opencv.core.Point> matches = new ArrayList<>();

            // Loading template image and target image
            Mat template  = templates[i].clone();
            Mat targetImg = undistortedImg.clone();

            // Pattern matching
            int widthMin = 20;      // [px]
            int widthMax = 100;     // [px]
            int changeWidth = 5;    // [px]
            int changeAngle = 45;   // [angle]

            //
            for(int width = widthMin; width <= widthMax; width += changeWidth)
            {
                for(int angle = 0; angle <= 360; angle += changeAngle)
                {
                    Mat resizedTemp = resizeImg(template, width);
                    Mat rotResizedTemp = rotImg(resizedTemp, angle);

                    Mat result = new Mat();
                    Imgproc.matchTemplate(targetImg, rotResizedTemp, result, Imgproc.TM_CCOEFF_NORMED);

                    // Get coordinates with similarity greater than or equal to the threshold
                    double threshold = 0.8;
                    Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);
                    double maxVal = mmlr.maxVal;
                    if(maxVal >= threshold)
                    {

                        Mat thresholdedResult = new Mat();
                        Imgproc.threshold(result, thresholdedResult, threshold, 1.0, Imgproc.THRESH_TOZERO);

                        // Get match counts
                        for(int y = 0; y < thresholdedResult.rows(); y++)
                        {
                            for(int x = 0; x < thresholdedResult.cols(); x++)
                            {
                                if(thresholdedResult.get(y, x)[0] > 0)
                                {
                                    matches.add(new org.opencv.core.Point(x, y));
                                }
                            }
                        }
                    }
                }
            }

            // Avoid detecting the same location
            List<org.opencv.core.Point> filteredMatches = removeDuplicates(matches);
            matchCnt += filteredMatches.size();

            // Number of matches for each template
            temp[i] = matchCnt;
        }

        return temp;
    }

    // Remove multiple detections
    private static List<org.opencv.core.Point> removeDuplicates(List<org.opencv.core.Point> points)
    {
        double length = 10; // Width 10px
        List<org.opencv.core.Point> filteredList = new ArrayList<>();

        for (org.opencv.core.Point point : points)
        {
            boolean isIncluded = false;
            for(org.opencv.core.Point checkPoint : filteredList)
            {
                double distance = calculateDistance(point, checkPoint);
                if(distance <= length)
                {
                    isIncluded = true;
                    break;
                }
            }

            if(!isIncluded)
            {
                filteredList.add(point);
            }
        }

        return filteredList;
    }

    // calculate distance
    private static double calculateDistance(org.opencv.core.Point p1, org.opencv.core.Point p2)
    {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
    }

    // Resize Image
    private static Mat resizeImg(Mat img, int width)
    {
        int height = (int) (img.rows() * ((double) width / img.cols()));
        Mat resizedImg = new Mat();
        Imgproc.resize(img, resizedImg, new Size(width, height));

        return resizedImg;
    }

    // Rotate image
    private static Mat rotImg(Mat img, int angle)
    {
        org.opencv.core.Point center = new org.opencv.core.Point(img.cols() / 2.0, img.rows() / 2.0);
        Mat rotatedMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat rotatedImg = new Mat();
        Imgproc.warpAffine(img, rotatedImg, rotatedMat, img.size());

        return rotatedImg;
    }

    // Get the maximum value of an array
    private static int getMaxIndex (int[] array)
    {
        int max = 0;
        int maxIndex = 0;

        for(int i = 0; i < array.length; i++)
        {
            if(array[i] > max)
            {
                max = array[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }
}

