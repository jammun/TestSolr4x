/**
 *  Copyleft (C) 2014 Jinho Lee
 *  All rights reserved.
 * 
 *  THIS SOFTWARE IS PROVIDED BY JINHO LEE ''AS IS'' AND ANY EXPRESS OR 
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
 *  NO EVENT SHALL KYLE GORMAN BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 *  TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 *  RequestValidator.java : To handle Solr Request, validation check for HttpRequest.
 *  @author Jinho Lee <jammun@gmail.com>, <jhlee@dlab.kr>
 *   
 */
package kr.dlab.biz;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrEventListener;
import org.apache.solr.request.SolrQueryRequest;
import org.w3c.dom.NodeList;


public class RequestValidator {

	private static RequestValidator myInstance;
	
	private RequestValidator() {
		super();
	}
	
	public static RequestValidator getInstance() {
		
		if ( myInstance == null ) {
			myInstance = new RequestValidator(); 
		}
		return myInstance;
	}
	
	public boolean isValidforRequest( SolrQueryRequest solrReq, SolrConfig config, String path) {
		
		boolean retVal = false;
		
		String reqUser = solrReq.getParams().get("user");
		
		//if user is not defined. return false
		if ( reqUser == null ) return retVal;
		
		for (PluginInfo info : config.getPluginInfos( "org.apache.solr.request.SolrRequestHandler") ) {
			
			if ( path.equals(info.name)) {

				List userArray = info.initArgs.getAll("users");
				
				if ( userArray == null || userArray.size() < 1 ) {
					return false;
				}
				NamedList users = (NamedList)userArray.get(0);
				
				List actualUser = users.getAll("user");
				
				for ( int i = 0; i < actualUser.size(); i++ ) {
					String userName = (String)actualUser.get(i);
					
					if ( reqUser.equals(userName)) {
						retVal = true;
						break;
					}
				}
			}
		}
		
		return retVal;
	}
	
}
