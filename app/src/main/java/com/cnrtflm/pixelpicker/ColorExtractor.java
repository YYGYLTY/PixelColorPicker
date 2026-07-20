package com.cnrtflm.pixelpicker;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColorExtractor {


    public static List<Integer> extractColors(Bitmap bitmap) {


        HashMap<Integer,Integer> colorMap =
                new HashMap<>();


        int width = bitmap.getWidth();
        int height = bitmap.getHeight();


        for(int x=0;x<width;x++){

            for(int y=0;y<height;y++){


                int color =
                        bitmap.getPixel(x,y);


                if(colorMap.containsKey(color)){

                    colorMap.put(
                            color,
                            colorMap.get(color)+1
                    );

                }else{

                    colorMap.put(
                            color,
                            1
                    );

                }


            }

        }



        List<Map.Entry<Integer,Integer>> list =
                new ArrayList<>(
                        colorMap.entrySet()
                );


        list.sort(
                (a,b)-> b.getValue()
                        -
                        a.getValue()
        );


        List<Integer> result =
                new ArrayList<>();


        int count=0;


        for(Map.Entry<Integer,Integer> entry:list){


            result.add(entry.getKey());


            count++;


            if(count>=16){

                break;

            }

        }


        return result;


    }


}