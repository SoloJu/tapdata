<div align="center">
<a href="https://tapdata.io/">
<img src="https://github.com/tapdata/tapdata-private/raw/master/assets/logo-orange-grey-bar.png" width="300px"/>
</a>
<br/><br/>

[![LICENSE](https://img.shields.io/github/license/tapdata/tapdata.svg)](https://github.com/tapdata/tapdata/blob/main/LICENSE)
[![Contributors](https://img.shields.io/github/contributors/tapdata/tapdata)](https://github.com/tapdata/tapdata/graphs/contributors)
[![Activity](https://img.shields.io/github/commit-activity/m/tapdata/tapdata)](https://github.com/tapdata/tapdata/pulse)
[![Release](https://img.shields.io/github/v/tag/tapdata/tapdata.svg?sort=semver)](https://github.com/tapdata/tapdata/releases)

</div>

---

[![Try It Online](<https://img.shields.io/badge/-Try%20It%20Online%20%E2%86%92-rgb(255,140,0)?style=for-the-badge>)](https://cloud.tapdata.net)
[![Official Website](<https://img.shields.io/badge/-Official%20Website%20%E2%86%92-rgb(59,71,229)?style=for-the-badge>)](https://cloud.tapdata.net)
[![Docs](<https://img.shields.io/badge/-Online%20Document%20%E2%86%92-rgb(0,205,102)?style=for-the-badge>)](https://docs.tapdata.io)


## What is Tapdata ?
Tapdata is a real-time data integration platform that enables data to be synchronized in real-time among various systems such as databases, SaaS services, applications, and files.
The synchronization tasks can be easily built through drag-and-drop operations, from table creation to full and incremental synchronization, all processes are fully automated.

1. [Key Features](https://docs.tapdata.io/cloud/introduction/features)
2. [Supported Connectors](https://docs.tapdata.io/cloud/introduction/supported-databases)

For more details, please read [docs](https://docs.tapdata.io/)
 
## Quick Start
### Start with cloud service
Tapdata service is available in cloud service, you can use fully-managed service, or deploy engine to your private network

Try on https://cloud.tapdata.io/, support google and github account login, free trial, NO credit card needed, start your real-time data journey immediately.

### Start with local deploy
RUN `docker run -d -p 3030:3030 github.com/tapdata/tapdata-opensource:latest`, wait for 3 minutes, then you can get it from http://localhost:3030/

default username is: admin@admin.com, default password is admin

## Examples
<details>
    <summary><h4>🗂️ Create Datasource and Test it</h4></summary>

1. Login tapdata platform

2. In the left navigation panel, click Connections

3. On the right side of the page, click Create

4. In the pop-up dialog, search and select MySQL

5. On the page that you are redirected to, follow the instructions below to fill in the connection information for MySQL

<img src="./assets/example-1-create-mysql-connection.jpg"></img>

6. Click Test, make sure all test pass, then click Save

<img src="./assets/example-1-test.jpg"></img>

</details>

<details>
    <summary><h4>🗂️ Sync Data From MySQL To MongoDB</h4></summary>

1. Create MySQL and MongoDB data source

2. In the left navigation panel, click Data Pipelines -> Data Replications

3. On the right side of the page, click Create

4. Drag and drop MySQL and MongoDB data sources onto the canvas

5. Drag a line from the MySQL data source to MongoDB

6. Configure the MySQL data source and select the data tables you want to synchronize

<img src='./assets/example-2-config-mysql.jpg'></img>

7. Click the Save button in the upper right corner, then click the Start button

8. Observe the indicators and events on the task page until data is in sync

<img src='./assets/example-2-metrics.jpg'></img>

</details>

<details>
    <summary><h4>🗂️ MySQL To PostgreSQL with Simple ETL</h4></summary>

1. Create MySQL and PostgreSQL data source

2. In the left navigation panel, click Data Pipelines -> Data Transformation

3. On the right side of the page, click Create

4. Drag and drop MySQL and PostgreSQL data sources onto the canvas

5. Drag a line from the MySQL data source to PostgreSQL

6. Click the plus sign on the connection line and select Field Rename

<img src='./assets/example-3-field-rename-1.jpg'></img>

7. Click Field Rename node, change i_price to price, i_data to data in config form

<img src='./assets/example-3-field-rename-2.jpg'></img>

8. Click the Save button in the upper right corner, then click the Start button

9. Observe the indicators and events on the task page until data is in sync

<img src='./assets/example-3-metrics.jpg'></img>

</details>

<details>
    <summary><h4>🗂️ Making materialized views in MongoDB</h4></summary>

Materialized view is a special feature of tapdata, You can give full play to the characteristics of MongoDB document database and create the data model you need, try enjoy it !

1. Create MySQL and MongoDB data source

2. In the left navigation panel, click Data Pipelines -> Data Transformation

3. On the right side of the page, click Create

4. 


</details>

<details>
    <summary><h4>🗂️ Data consistency check</h4></summary>

Using the data verification feature, you can quickly check whether the synchronized data is consistent and accurate

1. In the left navigation panel, click Data Pipelines -> Data Validation

2. On the right side of the page, click Task Consistency Validation

3. Choose 1 task, and valid type choose "All Fields Validation", it means system will check all fields for all record

<img src='./assets/example-5-config.jpg'></img>

4. Click Save, then click Execute in the task list

5. Wait validation task finished, click Result in the task list, and check the validation result

<img src='./assets/example-5-result.jpg'></img>

</details>

## Architecture


## License
Tapdata is under the Apache 2.0 license. See the [LICENSE](https://github.com/tapdata/tapdata/blob/main/LICENSE) file for details.

## Join now
- [Send Email](mailto:team@tapdata.io)
- [Slack channel](https://join.slack.com/t/tapdatacommunity/shared_invite/zt-1biraoxpf-NRTsap0YLlAp99PHIVC9eA)
