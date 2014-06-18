/**
 * 
 */
package kr.dlab.biz;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.servlet.SolrDispatchFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.dlab.db.DBSingleTon;

/**
 * @author admin
 *
 */
public class DBHandler {

	private Logger myLogger;
	
	private final String selectTableName = "t_logs_solr_search";
	private final String[]  selectLogColumns = { "log_id" ,
			  "added_date" ,
			  "user_ip" ,
			  "user_name",
			  "query_full",
			  "query_q" ,
			  "query_fq",
			  "result_cnt",
			  "facet_yn",
			  "etc_char1",
			  "etc_int1",
			  "etc_char2",
			  "etc_int2" ,
			  "etc_char3",
			  "etc_int3" } ;
	
	public DBHandler() {
	    try {
	        myLogger = LoggerFactory.getLogger(DBHandler.class);
	      } catch (NoClassDefFoundError e) {
	        throw new SolrException(
	            ErrorCode.SERVER_ERROR,
	            "Could not find necessary SLF4j logging jars. If using Jetty, the SLF4j logging jars need to go in "
	            +"the jetty lib/ext directory. For other containers, the corresponding directory should be used. "
	            +"For more information, see: http://wiki.apache.org/solr/SolrLogging",
	            e);
	      }
	}
	
	public void selectTest() {
		
		PreparedStatement pstmt;
		ResultSet rs;
		
		String query = " select 1 as count from dual";
		
		Connection con = DBSingleTon.getInstance().getConnection();
		
		try{
			pstmt = con.prepareStatement(query);
			
			rs = pstmt.executeQuery();
			rs.next();
			
			int cnt = rs.getInt("count");
			
			myLogger.info(" dual cnt = " + cnt);
			
		}catch(SQLException e){
			e.printStackTrace();
			myLogger.error("applist Error "+e.getErrorCode()+", "+e.getMessage()+","+e.getSQLState()+","+e.getStackTrace().toString());
		}catch(Exception e1){
			e1.printStackTrace();
			myLogger.error("applist Error "+", "+e1.getMessage()+","+e1.getStackTrace().toString());
		}finally{
			DBSingleTon.getInstance().conClose(con);
		}
	}
	
	public boolean insertLogSearch(HttpServletRequest req, SolrQueryRequest solrReq, 
			SolrQueryResponse solrRsp, String contextUri, String userIp) {
		
		boolean retVal = false;
		
		int solrRows = 0; 
		int solrHits = 0;
		int actualDocs = 0;
		
		String solrFullQuery = "";
		String solrUser = "";
		String solrQ = "";
		String solrFQ = "";
		String solrFacetYn = "";
		
		solrRows = solrReq.getParams().getInt("rows");
		solrHits  = (Integer)solrRsp.getToLog().get("hits");
		
		solrFullQuery = solrReq.getParamString();
		solrUser = solrReq.getParams().get("user");
		solrQ = solrReq.getParams().get("q");
		solrFQ = solrReq.getParams().get("fq");
		solrFacetYn = solrReq.getParams().get("facet");
		
		if (  solrHits > solrRows ) {
			actualDocs = solrRows;
		} else if ( solrHits < solrRows ) {
			actualDocs = solrHits;
		} else {
			actualDocs = solrRows;
		}
		
		if ( solrFQ == null ) solrFQ = "";
		solrFacetYn = ( solrFacetYn == null) ? "N" : "Y";
		
		PreparedStatement pstmt;
		
		StringBuffer columnNm = new StringBuffer();
		StringBuffer questionNm = new StringBuffer();
		
		int[] idxs = {0, 1};
		String[] qq = {"uuid(),", "now(),"};
		
		makeCustomSql(columnNm, questionNm, idxs, qq);
		
		String query = "INSERT INTO " + selectTableName + 
							columnNm.toString() + " values " + questionNm.toString() ; 
		
		myLogger.info("SQL = " + query);
		
		Connection con = DBSingleTon.getInstance().getConnection();
		
		try{
			pstmt = con.prepareStatement(query);
			
			pstmt.setString(1, userIp );
			pstmt.setString(2, solrUser );
			pstmt.setString(3, solrFullQuery );
			pstmt.setString(4, solrQ );
			pstmt.setString(5, solrFQ );
			pstmt.setInt(6, actualDocs);
			pstmt.setString(7, solrFacetYn);
			pstmt.setString(8, "");
			pstmt.setInt(9, 0);
			pstmt.setString(10, "");
			pstmt.setInt(11, 0);
			pstmt.setString(12, "");
			pstmt.setInt(13, 0);
			
			pstmt.execute();
			
		}catch(SQLException e){
			//e.printStackTrace();
			myLogger.error("Error "+e.getErrorCode()+", "+e.getMessage()+","+e.getSQLState()+","+e.getStackTrace().toString());
		}catch(Exception e1){
			//e1.printStackTrace();
			myLogger.error("Error "+", "+e1.getMessage()+","+e1.getStackTrace().toString());
		}finally{
			DBSingleTon.getInstance().conClose(con);
		}
		
		return retVal;
	}

	/**
	 * @param columnNm
	 * @param questionNm
	 */
	private void makeSqlString(StringBuffer columnNm, StringBuffer questionNm) {
		
		columnNm.append("(");
		questionNm.append("(");
		
		for ( int i = 0; i < selectLogColumns.length; i++ ) {
			columnNm.append( selectLogColumns[i] );
			questionNm.append( "?" );
			if ( i != selectLogColumns.length -1) {
				columnNm.append( ",");
				questionNm.append( ",");
			} 
		}
		columnNm.append(")");
		questionNm.append(")");
	}
	
	/**
	 * @param columnNm
	 * @param questionNm
	 */
	private String[] makeCustomSql(StringBuffer columnNm, StringBuffer questionNm, int[] idx, String[] query) {
		
		columnNm.append("(");
		questionNm.append("(");
		
		String[] qList = new String[selectLogColumns.length];
		
		for ( int i = 0; i < selectLogColumns.length; i++ ) {
			columnNm.append( selectLogColumns[i] );
			if ( i != selectLogColumns.length -1) {
				columnNm.append( ",");
				qList[i] = "?,";
			} else {
				qList[i] = "?";
			}
		}
		
		for (int i =0; i < idx.length; i++ ) {
			qList[ idx[i] ] = query[i];
		}
		
		for ( int i = 0; i < qList.length; i++ ) {
			questionNm.append( qList[i] );
		}
		
		columnNm.append(")");
		questionNm.append(")");
		
		return qList;
	}
	
	public boolean insertLogIndex() {
		boolean retVal = false;
		
		return retVal;
	}

}
