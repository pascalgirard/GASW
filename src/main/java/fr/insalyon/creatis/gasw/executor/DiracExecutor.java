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
package fr.insalyon.creatis.gasw.executor;

import fr.insalyon.creatis.gasw.Constants;
import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.GaswInput;
import fr.insalyon.creatis.gasw.executor.generator.jdl.JdlGenerator;
import fr.insalyon.creatis.gasw.monitor.MonitorFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.log4j.Logger;

/**
 *
 * @author Rafael Silva
 */
public class DiracExecutor extends Executor {

    private static final Logger log = Logger.getLogger(DiracExecutor.class);

    protected DiracExecutor(String version, GaswInput gaswInput) {
        super(version, gaswInput);
    }

    @Override
    public void preProcess() {
        scriptName = generateScript();
        jdlName = generateJdl(scriptName);
    }

    @Override
    public String submit() throws GaswException {
        super.submit();
        try {
            String exec = "dirac-wms-job-submit " + Constants.JDL_ROOT + "/" + jdlName;
            Process process = Runtime.getRuntime().exec(exec);
            process.waitFor();
            
            if (process.exitValue() != 0) {
                throw new GaswException("Unable to submit job.");
            }

            BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String cout = "";
            String s = null;
            while ((s = r.readLine()) != null) {
                cout += s;
            }

            String jobID = cout.substring(cout.lastIndexOf("=") + 2, cout.length()).trim();
            try {
                new Integer(jobID);
            } catch (NumberFormatException ex) {
                throw new GaswException("Unable to submit job. DIRAC Error: " + cout);
            }
            MonitorFactory.getMonitor(version).add(jobID, gaswInput.getRelease().getSymbolicName(), jdlName);

            log.info("Dirac Executor Job ID: " + jobID);

            return jobID;

        } catch (InterruptedException ex) {
            logException(log, ex);
            return null;
        } catch (IOException ex) {
            logException(log, ex);
            return null;
        }
    }

    /**
     * 
     * @param scriptName
     * @return
     */
    private String generateJdl(String scriptName) {

        StringBuilder sb = new StringBuilder();
        JdlGenerator generator = JdlGenerator.getInstance();
        
        sb.append(generator.generate(scriptName));

        return publishJdl(scriptName, sb.toString());
    }
}
