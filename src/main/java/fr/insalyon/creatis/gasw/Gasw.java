/* Copyright CNRS-CREATIS
 *
 * Rafael Silva
 * rafael.silva@creatis.insa-lyon.fr
 * http://www.creatis.insa-lyon.fr/~silva
 *
 * This software is a grid-enabled data-driven workflow manager and editor.
 *
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */
package fr.insalyon.creatis.gasw;

import fr.insalyon.creatis.gasw.executor.Executor;
import fr.insalyon.creatis.gasw.executor.ExecutorFactory;
import fr.insalyon.creatis.gasw.monitor.MonitorFactory;
import fr.insalyon.creatis.gasw.output.OutputUtilFactory;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Rafael Silva
 */
public class Gasw {

    private static Gasw instance;
    private GaswNotification notification;
    private Object client;
    private List<String> finishedJobs;

    /**
     * Gets an instance of GASW
     * 
     * @return Instance of GASW
     */
    public synchronized static Gasw getInstance() throws GaswException {
        if (instance == null) {
            instance = new Gasw();
        }
        return instance;
    }

    private Gasw() throws GaswException {
        Configuration.setUp();
        finishedJobs = new ArrayList<String>();
        notification = new GaswNotification();
        notification.start();
    }

    /**
     * Performs the execution of the command.
     *
     * @param client
     * @param version
     * @param command Command to be performed.
     * @param parameters List of parameters associated with the command.
     * @param downloads List of input files to be downloaded in the worker node.
     * @param uploads List of output files to be uploaded to a Storage Element.
     * @return Job identification
     */
    public synchronized String submit(Object client, String version, String command, List<String> parameters, List<URI> downloads, List<URI> uploads) {

        if (this.client == null) {
            this.client = client;
        }
        Executor executor = ExecutorFactory.getExecutor(version, command, parameters, downloads, uploads);
        executor.preProcess();
        return executor.submit();
    }

    /**
     * 
     * @param finishedJobs
     */
    public synchronized void addFinishedJob(List<String> finishedJobs) {
        this.finishedJobs.addAll(finishedJobs);
    }

    /**
     * Gets the standard output and error files of all finished jobs
     * @return Map with the standard output and error files respectively for each job.
     */
    public synchronized Map<String, File[]> getOutputs() {

        Map<String, File[]> outputsMap = new HashMap<String, File[]>();
        List<String> jobsToRemove = new ArrayList<String>();

        if (finishedJobs != null) {
            for (String jobID : finishedJobs) {
                String version = jobID.contains("Local-") ? "LOCAL" : "GRID";
                File[] outputs = OutputUtilFactory.getOutputUtil(version).getOutputs(jobID.split("--")[0]);
                outputsMap.put(jobID, outputs);
                jobsToRemove.add(jobID);
            }
            finishedJobs.removeAll(jobsToRemove);
        }

        return outputsMap;
    }

    public synchronized void terminate() {
        MonitorFactory.terminate();
        notification.terminate();
    }

    private class GaswNotification extends Thread {

        private boolean stop = false;

        @Override
        public void run() {
            super.run();

            while (!stop) {
                synchronized (this) {
                    if (finishedJobs != null && finishedJobs.size() > 0) {
                        synchronized (client) {
                            client.notify();
                        }
                    }
                }
                try {
                    sleep(10000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }

        public void terminate() {
            stop = true;
        }
    }
}