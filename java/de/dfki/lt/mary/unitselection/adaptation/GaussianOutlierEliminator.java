/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */

package de.dfki.lt.mary.unitselection.adaptation;

import java.io.IOException;
import java.util.Arrays;

import de.dfki.lt.mary.unitselection.adaptation.codebook.WeightedCodebook;
import de.dfki.lt.mary.unitselection.adaptation.codebook.WeightedCodebookFile;
import de.dfki.lt.mary.unitselection.adaptation.codebook.WeightedCodebookFileHeader;
import de.dfki.lt.signalproc.util.MathUtils;

/**
 * @author oytun.turk
 *
 */
public class GaussianOutlierEliminator {
    public double totalStandardDeviationsLsf;
    public double totalStandardDeviationsF0;
    public double totalStandardDeviationsDuration;
    public double totalStandardDeviationsEnergy;
    public static final double DEFAULT_TOTAL_STANDARD_DEVIATIONS = 1.5;
    
    public GaussianOutlierEliminator()
    {   
        this(DEFAULT_TOTAL_STANDARD_DEVIATIONS, DEFAULT_TOTAL_STANDARD_DEVIATIONS, DEFAULT_TOTAL_STANDARD_DEVIATIONS, DEFAULT_TOTAL_STANDARD_DEVIATIONS);
    }
    
    public GaussianOutlierEliminator(double totalStandardDeviationsLsfIn, 
                                     double totalStandardDeviationsF0In, 
                                     double totalStandardDeviationsDuraitonIn, 
                                     double totalStandardDeviationsEnergyIn)
    {   
        init(totalStandardDeviationsLsfIn, totalStandardDeviationsF0In, totalStandardDeviationsDuraitonIn, totalStandardDeviationsEnergyIn);
    }
    
    public void init(double totalStandardDeviationsLsfIn, 
                     double totalStandardDeviationsF0In, 
                     double totalStandardDeviationsDuraitonIn, 
                     double totalStandardDeviationsEnergyIn)
    {
        totalStandardDeviationsLsf = totalStandardDeviationsLsfIn;
        totalStandardDeviationsF0 = totalStandardDeviationsF0In;
        totalStandardDeviationsDuration = totalStandardDeviationsDuraitonIn;
        totalStandardDeviationsEnergy = totalStandardDeviationsEnergyIn;
    }

    public void eliminate(String codebookFileIn, String codebookFileOut)
    {
        WeightedCodebookFile fileIn = new WeightedCodebookFile(codebookFileIn, WeightedCodebookFile.OPEN_FOR_READ);
        
        WeightedCodebook codebookIn = null;
        
        try {
            codebookIn = fileIn.readCodebookFile();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        if (codebookIn!=null)
        {
            int[] acceptanceStatus = new int[codebookIn.header.totalLsfEntries];
            double[] lsfDistances = new double[codebookIn.header.totalLsfEntries];
            double[] f0Distances = new double[codebookIn.header.totalLsfEntries];
            int[] voicedInds = new int[codebookIn.header.totalLsfEntries];
            double[] durationDistances = new double[codebookIn.header.totalLsfEntries];
            double[] energyDistances = new double[codebookIn.header.totalLsfEntries];
            Arrays.fill(acceptanceStatus, OutlierStatus.NON_OUTLIER);
            Arrays.fill(voicedInds, -1);
            
            OutlierStatus status = new OutlierStatus();
            
            double lsfDistanceMean = 0.0;
            double lsfDistanceStdDev = 0.0;
            double f0DistanceMean = 0.0;
            double f0DistanceStdDev = 0.0;
            int totalVoiced = 0;
            double durationDistanceMean = 0.0;
            double durationDistanceStdDev = 0.0;
            double energyDistanceMean = 0.0;
            double energyDistanceStdDev = 0.0;
            
            int i;
            
            //Estimate mean of distances between source and target entries
            for (i=0; i<codebookIn.header.totalLsfEntries; i++)
            {
                lsfDistances[i] = DistanceComputer.getLsfInverseHarmonicDistance(codebookIn.lsfEntries[i].sourceItem.lsfs, codebookIn.lsfEntries[i].targetItem.lsfs);
                
                if (codebookIn.lsfEntries[i].sourceItem.f0>10.0 && codebookIn.lsfEntries[i].targetItem.f0>10.0)
                {
                    f0Distances[totalVoiced] = codebookIn.lsfEntries[i].sourceItem.f0-codebookIn.lsfEntries[i].targetItem.f0;
                    voicedInds[totalVoiced] = i;
                    totalVoiced++;
                }
                
                durationDistances[i] = codebookIn.lsfEntries[i].sourceItem.duration-codebookIn.lsfEntries[i].targetItem.duration;
                energyDistances[i] = codebookIn.lsfEntries[i].sourceItem.energy-codebookIn.lsfEntries[i].targetItem.energy;
            }
            
            lsfDistanceMean = MathUtils.mean(lsfDistances);
            f0DistanceMean = MathUtils.mean(f0Distances, 0, totalVoiced-1);
            durationDistanceMean = MathUtils.mean(durationDistances);
            energyDistanceMean = MathUtils.mean(energyDistances);
            //
            
            //Estimate standard deviation of distances between source and target
            if (codebookIn.header.totalLsfEntries>1)
            {
                lsfDistanceStdDev = MathUtils.standardDeviation(lsfDistances, lsfDistanceMean);
                durationDistanceStdDev = MathUtils.standardDeviation(durationDistances, durationDistanceMean);
                energyDistanceStdDev = MathUtils.standardDeviation(energyDistances, energyDistanceMean);
            }
            else
            {
                lsfDistanceStdDev = 0.5*Double.MAX_VALUE;
                durationDistanceStdDev = 0.5*Double.MAX_VALUE;;
                energyDistanceStdDev = 0.5*Double.MAX_VALUE;;  
            }
            
            if (totalVoiced>1)
                f0DistanceStdDev = MathUtils.standardDeviation(f0Distances, f0DistanceMean, 0, totalVoiced-1);
            else
                f0DistanceStdDev = 0.5*Double.MAX_VALUE;
            //
            
            int totalLsfOutliers = 0;
            int totalDurationOutliers = 0;
            int totalF0Outliers = 0;
            int totalEnergyOutliers = 0;
            for (i=0; i<codebookIn.header.totalLsfEntries; i++)
            {
                if (lsfDistances[i]>lsfDistanceMean+totalStandardDeviationsLsf*lsfDistanceStdDev)
                {
                    acceptanceStatus[i] += OutlierStatus.LSF_OUTLIER;
                    totalLsfOutliers++;
                }

                if (durationDistances[i]>durationDistanceMean+totalStandardDeviationsDuration*durationDistanceStdDev)
                {
                    acceptanceStatus[i] += OutlierStatus.DURATION_OUTLIER;
                    totalDurationOutliers++;
                }
                
                if (energyDistances[i]>energyDistanceMean+totalStandardDeviationsEnergy*energyDistanceStdDev)
                {
                    acceptanceStatus[i] += OutlierStatus.ENERGY_OUTLIER;
                    totalEnergyOutliers++;
                }
                
            }
            
            for (i=0; i<totalVoiced; i++)
            {
                if (f0Distances[i]>f0DistanceMean+totalStandardDeviationsF0*f0DistanceStdDev)
                {
                    acceptanceStatus[voicedInds[i]] += OutlierStatus.F0_OUTLIER;
                    totalF0Outliers++;
                }
            }
            
            int newTotalEntries = 0;
            for (i=0; i<codebookIn.header.totalLsfEntries; i++)
            {
                if (acceptanceStatus[i]==OutlierStatus.NON_OUTLIER)
                    newTotalEntries++;
            }
            
            //Write the output codebook
            WeightedCodebookFile codebookOut = new WeightedCodebookFile(codebookFileOut, WeightedCodebookFile.OPEN_FOR_WRITE);
            
            WeightedCodebookFileHeader headerOut = new WeightedCodebookFileHeader(codebookIn.header);
            headerOut.resetTotalEntries();
            codebookOut.writeCodebookHeader(headerOut);

            for (i=0; i<codebookIn.header.totalLsfEntries; i++)
            {
                if (acceptanceStatus[i]==OutlierStatus.NON_OUTLIER)
                    codebookOut.writeLsfEntry(codebookIn.lsfEntries[i]);
            }
            
            //Append pitch statistics
            for (i=0; i<codebookIn.header.totalF0StatisticsEntries; i++)
                codebookOut.writeF0StatisticsEntry(codebookIn.f0StatisticsCollection.entries[i]);
            //
            
            codebookOut.close();
            //
            
            System.out.println("Outliers detected = " + String.valueOf(codebookIn.header.totalLsfEntries-newTotalEntries) + " of " + String.valueOf(codebookIn.header.totalLsfEntries));
            System.out.println("Total lsf outliers = " + String.valueOf(totalLsfOutliers));
            System.out.println("Total f0 outliers = " + String.valueOf(totalF0Outliers));
            System.out.println("Total duration outliers = " + String.valueOf(totalDurationOutliers));
            System.out.println("Total energy outliers = " + String.valueOf(totalEnergyOutliers));

        }
    }
}