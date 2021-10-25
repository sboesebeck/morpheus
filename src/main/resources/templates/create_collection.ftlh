<!DOCTYPE html>
<html lang="en">

<head>
    <#include "includes/header.ftlh">
</head>

<body id="page-top">

<!-- Page Wrapper -->
<div id="wrapper">

    <#include "includes/sidebar.ftlh">

    <!-- Content Wrapper -->
    <div id="content-wrapper" class="d-flex flex-column">
        <#include "includes/topbar.ftlh"/>
        <!-- Main Content -->
        <div id="content">

            <!-- Begin Page Content -->
            <div class="container-fluid">

                <div id="contents">
                    <ul>
                        <div class="card shadow mb-4">
                            <div class="card-header py-3">
                                <h6 class="m-0 font-weight-bold text-primary"> Create Collection</h6>
                            </div>
                            <pre class="small">
        db.createCollection( &lt;name&gt;,
           {
             capped: &lt;boolean&gt;,
             timeseries: {                  // Added in MongoDB 5.0
                timeField: &lt;string&gt;,        // required for time series collections
                metaField: &lt;string&gt;,
                granularity: &lt;string&gt;
             },
             expireAfterSeconds: &lt;number&gt;,
             autoIndexId: &lt;boolean&gt;,
             size: &lt;number&gt;,
             max: &lt;number&gt;,
             storageEngine: &lt;document&gt;,
             validator: &lt;document&gt;,
             validationLevel: &lt;string&gt;,
             validationAction: &lt;string&gt;,
             indexOptionDefaults: &lt;document&gt;,
             viewOn: &lt;string&gt;,              // Added in MongoDB 3.4
             pipeline: &lt;pipeline&gt;,          // Added in MongoDB 3.4
             collation: &lt;document&gt;,         // Added in MongoDB 3.4
             writeConcern: &lt;document&gt;
           }
                            )
                        </pre>
                            <b class="card-body">
                                <div>
                                    <@helplink "Name:" "helpName" />
                                    <input class="form-control bg-light border-1" v-model="name">
                                    <@helpdlg "helpName" "Help for collectionName" "Type: <i>String</i> The name of the collection
                    to create. See Naming Restrictions." "https://docs.mongodb.com/manual/reference/limits/#std-label-restrictions-on-db-names"/>
                                </div>
                                <br>
                                <hr class="sidebar-divider">
                                <div>
                                    <input type="checkbox" class="custom-checkbox" v-model="capped"><@helplink "Capped:" "helpCapped"/>
                                    <@helpdlg "helpCapped" "Capped collection" "Optional. To create acapped collection, specify true. If you specify true, you must also set a maximum size in the size field." "https://docs.mongodb.com/manual/reference/glossary/#std-term-capped-collection"/>
                                </div>
                                <div>
                                    <@helplink "Size:" "helpSize"/>
                                    <input class="form-control bg-light border-1" v-model="size">
                                    <@helpdlg "helpSize" "Help for CapSize" "Optional. Specify a maximum size in bytes for a capped collection. Once a capped collection reaches its maximum size, MongoDB removes the older documents to make space for the new documents. The size field is required for capped collections and ignored for other collections." ""/>
                                </div>
                                <div>
                                    <@helplink "Max:" "helpMax"/> <input class="form-control bg-light border-1" v-model="max">
                                    <@helpdlg "helpMax" "Help Capped Max Size" "Optional. The maximum number of documents allowed in the capped collection. The size limit takes precedence over this limit. If a capped collection reaches the size limit before it reaches the maximum number of documents, MongoDB removes old documents. If you prefer to use the max limit, ensure that the size limit, which is required for a capped collection, is sufficient to contain the maximum number of documents." ""/>

                                </div>
                                <hr class="sidebar-divider">
                                <div>
                                    <@helplink "Storage Engine Options:" "storageEngineHelp"/> <textarea class="form-control bg-light border-1"
                                                              v-model="storageEngine"></textarea>
                                    <@helpdlg "storageEngineHelp" "Storage Engine Options" "Please define JSON!
                                    Optional. Available for the WiredTiger storage engine only.

Allows users to specify configuration to the storage engine on a per-collection basis when creating a collection. The value of the storageEngine option should take the following form:

{ <storage-engine-name>: <options> }

Storage engine configuration specified when creating collections are validated and logged to the oplog during replication to support replica sets with members that use different storage engines.
                                    " "https://docs.mongodb.com/manual/reference/method/db.createCollection/#std-label-create-collection-storage-engine-options"></@helpdlg>
                                </div>
                                <br>
                                <hr class="sidebar-divider">
                                <br>
                                <div>
                                    Validator: <textarea class="form-control bg-light border-1"
                                                         v-model="validator"></textarea>
                                    ValidationLevel: <input class="form-control bg-light border-1"
                                                            v-model="validationLevel">
                                    ValidationAction: dropdown...
                                </div>
                                <br>
                                <hr class="sidebar-divider">
                                <br>
                                <div>
                                    Collation: <textarea class="form-control bg-light border-1"
                                                         v-model="collation"></textarea>
                                </div>
                                <hr class="sidebar-divider">
                                <div>
                                    view On: <input class="form-control bg-light border-1"
                                                    v-model="viewOn">
                                </div>
                                <hr class="sidebar-divider">
                                <div>
                                    TimeSeries->Timefield: <input class="form-control bg-light border-1"
                                                                  v-model="timeseries.timeField"><br>
                                    TimeSeries->Metafield: <input class="form-control bg-light border-1"
                                                                  v-model="timeseries.metaField"><br>
                                    TimeSeries->Granulatiry: <input class="form-control bg-light border-1"
                                                                    v-model="timeseries.granularity"><br>
                                </div>
                                <hr class="sidebar-divider">

                                <div>
                                    ExpireAfterSeconds: <input class="form-control bg-light border-1"
                                                               v-model="expireAfterSeconds">
                                </div>
                                <br>
                            </b>
                        </div>
                    </ul>
                    <button class="btn btn-primary" @click="createCollection">create Collection {{name}}</button>
                </div>
            </div>
            <!-- /.container-fluid -->

        </div>
        <!-- End of Main Content -->

        <!-- Footer -->
        <#include "includes/footer.ftlh"/>
        <!-- End of Footer -->

    </div>
    <!-- End of Content Wrapper -->

</div>
<!-- End of Page Wrapper -->

<!-- Scroll to Top Button-->
<a class="scroll-to-top rounded" href="#page-top">
    <i class="fas fa-angle-up"></i>
</a>


<script type="text/javascript">
    const collectionOpts = {
        data() {
            return {
                name: "",
                capped: false,
                timeseries: {                  // Added in MongoDB 5.0
                    timeField: "",        // required for time series collections
                    metaField: "",
                    granularity: ""
                },
                expireAfterSeconds: 0,
                size: 0,
                max: 0,
                storageEngine: "",
                validator: "",
                validationLevel: "",
                validationAction: "",
                indexOptionDefaults: "",
                viewOn: "",              // Added in MongoDB 3.4
                pipeline: "",          // Added in MongoDB 3.4
                collation: "",         // Added in MongoDB 3.4
                writeConcern: ""
            };
        },
        methods: {
            createCollection(){

                var xhr = new XMLHttpRequest();
                xhr.open("POST", "/createCollection");

                xhr.setRequestHeader("Accept", "application/json");
                xhr.setRequestHeader("Content-Type", "application/json");

                xhr.onreadystatechange = function () {
                    if (xhr.readyState === 4) {
                        console.log(xhr.status);
                        console.log(xhr.responseText);
                    }
                };

                var data =  JSON.stringify(this);
                xhr.send(data);
                document.location.href="/";
            }
        }
    };

    const app = Vue.createApp(collectionOpts);
    app.mount("#contents");
</script>

<!-- Bootstrap core JavaScript-->
<script src="/vendor/jquery/jquery.min.js"></script>
<script src="/vendor/bootstrap/js/bootstrap.bundle.min.js"></script>

<!-- Core plugin JavaScript-->
<script src="/vendor/jquery-easing/jquery.easing.min.js"></script>

<!-- Custom scripts for all pages-->
<script src="/js/sb-admin-2.min.js"></script>

</body>

</html>


<#macro helplink label id>
    <a class="text-black-50" href="#" data-toggle="modal" data-target="#${id}">
        &nbsp;${label}
        <i class="fas fa-question fa-sm fa-fw mr-2 text-gray-400"></i>
    </a>
</#macro>

<#macro helpdlg id title helptext url>
    <!-- Logout Modal-->
    <div class="modal fade" id="${id}" tabindex="-1" role="dialog"
         aria-labelledby="modal${id}"
         aria-hidden="true">
        <div class="modal-dialog" role="document">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title" id="modal${id}">${title}</h5>
                    <button class="close" type="button" data-dismiss="modal"
                            aria-label="Close">
                        <span aria-hidden="true">×</span>
                    </button>
                </div>
                <div class="modal-body">${helptext}   </div>
                <div class="modal-footer">
                    <button class="btn btn-primary" type="button" data-dismiss="modal">
                        ok
                    </button>
                    <#if url!="">
                    <button class="btn btn-secondary" type="button" data-dismiss="modal" onclick="window.open('${url}');">
                        more info
                    </button></#if>
                </div>
            </div>
        </div>
    </div>

</#macro>