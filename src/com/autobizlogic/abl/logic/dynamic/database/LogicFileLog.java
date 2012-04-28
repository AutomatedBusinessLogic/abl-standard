package com.autobizlogic.abl.logic.dynamic.database;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * An instance of this gets created every time a new set of logic classes is read.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */

@Entity
@Table(name="abl_logic_file_log")
public class LogicFileLog {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ident", nullable=false)
	public long getIdent() { return ident; }
	public void setIdent(long ident) { this.ident = ident; }
	private long ident;

	@Column(name="client_name", nullable=false, length=300)
	public String getClientName() { return clientName; }
	public void setClientName(String clientName) { this.clientName = clientName; }
	private String clientName;

	@Column(name="client_status", length=1000)
	public String getClientStatus() { return clientStatus; }
	public void setClientStatus(String clientStatus) { this.clientStatus = clientStatus; }
	private String clientStatus;

	@Column(name="log_date")
	public Timestamp getLogDate() { return logDate; }
	public void setLogDate(Timestamp logDate) { this.logDate = logDate; }
	private Timestamp logDate;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="logic_file_ident", nullable=false)
	public LogicFile getLogicFile() { return logicFile; }
	public void setLogicFile(LogicFile logicFile) { this.logicFile = logicFile; }
	private LogicFile logicFile;
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 