/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package xxl.util;

import java.util.Iterator;
import xxl.util.statistics.StatisticCenter;
import xxl.core.collections.containers.io.ConverterContainer;
import xxl.core.functions.Function;
import xxl.core.io.converters.ConvertableConverter;
import xxl.core.spatial.KPE;
import xxl.core.spatial.rectangles.Rectangle;

/**
 *
 * @author joao
 */
public class MaxDoubleRTree extends StarRTree{

    public MaxDoubleRTree(StatisticCenter statisticCenter, String id, String outputPath,
            int dimensions, int bufferSize, int blockSize, int minCapacity, int maxCapacity){
        super(statisticCenter, id, outputPath, dimensions, bufferSize, blockSize, minCapacity, maxCapacity);
    }

    @Override
    public Rectangle rectangle (Object entry) {
        return (Rectangle) ((MaxDoubleRectangle)descriptor(entry)).clone();
    }

    @Override
    protected ConverterContainer createConverterContainer(final int dimensions){
        return new ConverterContainer(
                    fileContainer,
                    this.nodeConverter(new ConvertableConverter(
                        new Function () {
                            @Override
                            public Object invoke () {
                                return new KPE(new MaxDoubleRectangle(dimensions));
                            }
                        }), this.indexEntryConverter(
                                new ConvertableConverter(
                                    new Function () {
                                        @Override
                                        public Object invoke () {
                                            return new MaxDoubleRectangle(dimensions);
                                        }
                                    }
                                )
                           )
                    )
                );
    }

    public void checkTree(IndexEntry n){
        double max=-1;
        double childScore=0;
        Object child;
        for(Iterator it = n.get().entries();it.hasNext();){
            child = it.next();
            if(child instanceof IndexEntry){
                childScore = ((MaxDoubleRectangle)((IndexEntry) child).descriptor()).getScore();
                checkTree((IndexEntry)child);
            }else{ //child instanceof KPE
                childScore = ((MaxDoubleRectangle)((KPE) child).getData()).getScore();
            }
            max = Math.max(max, childScore);
        }
        if(max!=((MaxDoubleRectangle)((IndexEntry) n).descriptor()).getScore()){
            throw new RuntimeException(" E R R O R ! -> Checking MaxStarRTree.");
        }
    }
}